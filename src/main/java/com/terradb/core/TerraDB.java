package com.terradb.core;

import com.terradb.buffer.BufferPool;
import com.terradb.buffer.BufferPoolMetrics;
import com.terradb.btree.BTree;
import com.terradb.btree.KV;
import com.terradb.common.StorageException;
import com.terradb.disk.DiskManager;
import com.terradb.page.Page;
import com.terradb.recovery.CheckpointFile;
import com.terradb.recovery.CheckpointInfo;
import com.terradb.recovery.RecoveryManager;
import com.terradb.wal.WalManager;
import com.terradb.wal.WalRecord;
import com.terradb.wal.WalRecordType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class TerraDB implements AutoCloseable {

    private final Path dir;
    private final int pageSize;
    private final DiskManager disk;
    private final WalManager wal;
    private final BufferPool bufferPool;
    private final BTree tree;
    private long nextOpId = 1;

    private TerraDB(Path dir, int pageSize, DiskManager disk, WalManager wal,
                    BufferPool bufferPool, BTree tree) {
        this.dir = dir;
        this.pageSize = pageSize;
        this.disk = disk;
        this.wal = wal;
        this.bufferPool = bufferPool;
        this.tree = tree;
    }

    public static TerraDB open(Path dir, int pageSize, int poolSize) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new StorageException("Cannot create directory " + dir, e);
        }
        Path db = dir.resolve("terradb.db");
        Path walPath = dir.resolve("terradb.wal");
        Path ckptPath = dir.resolve("terradb.ckpt");

        RecoveryManager.recoverFromCheckpoint(db, walPath, ckptPath, pageSize);

        DiskManager disk = DiskManager.open(db, pageSize);
        WalManager wal = WalManager.open(walPath, pageSize);
        BufferPool pool = new BufferPool(disk, poolSize, true);
        BTree tree = new BTree(disk, pool);
        return new TerraDB(dir, pageSize, disk, wal, pool, tree);
    }

    public void put(long key, long value) {
        tree.put(key, value);
        commit(CrashPoint.NONE);
    }

    public boolean delete(long key) {
        boolean removed = tree.delete(key);
        commit(CrashPoint.NONE);
        return removed;
    }

    public Long get(long key) {
        return tree.get(key);
    }

    public List<KV> scan(long startKey, long endKey) {
        return tree.scan(startKey, endKey);
    }

    public BufferPoolMetrics metrics() {
        return bufferPool.metrics;
    }

    public Path dir() { return dir; }

    /** Flush everything, write a checkpoint record + checkpoint file. */
    public void checkpoint() {
        commit(CrashPoint.NONE);
        bufferPool.flushAll();
        disk.sync();

        long lsn = wal.nextLsn();
        Page meta = disk.buildMetaPage();
        meta.setPageLsn(lsn);
        wal.append(WalRecordType.CHECKPOINT, nextOpId++,
                List.of(new WalRecord.PageImage(0, meta.toBytes())));
        wal.sync();

        CheckpointFile.write(dir.resolve("terradb.ckpt"),
                new CheckpointInfo(lsn, wal.currentOffset()));
    }

    /** Put one key, then HARD-KILL the process at the chosen crash point. */
    public void putWithCrash(long key, long value, CrashPoint point) {
        tree.put(key, value);
        if (point == CrashPoint.AFTER_DATA_MOD) Runtime.getRuntime().halt(7);

        commit(point);

        if (point == CrashPoint.AFTER_DATA_FLUSH) {
            bufferPool.flushAll();
            disk.sync();
            Runtime.getRuntime().halt(7);
        }
        if (point == CrashPoint.BEFORE_CHECKPOINT_COMPLETE) {
            bufferPool.flushAll();
            disk.sync();
            Runtime.getRuntime().halt(7);   // checkpoint record/file never written
        }
    }

    private void commit(CrashPoint point) {
        List<Page> uncommitted = bufferPool.collectUncommitted();
        if (uncommitted.isEmpty()) return;

        long lsn = wal.nextLsn();
        List<WalRecord.PageImage> images = new ArrayList<>();

        Page meta = disk.buildMetaPage();
        meta.setPageLsn(lsn);
        images.add(new WalRecord.PageImage(0, meta.toBytes()));

        for (Page p : uncommitted) {
            p.setPageLsn(lsn);
            images.add(new WalRecord.PageImage(p.getPageId(), p.toBytes()));
        }

        if (point == CrashPoint.BEFORE_WAL) Runtime.getRuntime().halt(7);

        wal.append(WalRecordType.OPERATION_COMMIT, nextOpId++, images);

        if (point == CrashPoint.AFTER_WAL_APPEND) Runtime.getRuntime().halt(7);

        wal.sync();

        if (point == CrashPoint.AFTER_WAL_FSYNC) Runtime.getRuntime().halt(7);

        bufferPool.markCommitted(lsn);
    }

    @Override
    public void close() {
        commit(CrashPoint.NONE);
        bufferPool.flushAll();
        disk.sync();
        disk.close();
        wal.close();
    }

    public void simulateCrash() {
        disk.abortClose();
        wal.close();
    }
}