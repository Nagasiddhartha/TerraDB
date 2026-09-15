package com.terradb.wal;

import com.terradb.common.StorageException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class WalManager implements AutoCloseable {

    private final Path walPath;
    private final int pageSize;
    private final FileChannel channel;

    private long nextLsn = 1;
    private long lastAppendedLsn = 0;
    private long lastSyncedLsn = 0;
    private long writePosition = 0;

    private WalManager(Path walPath, int pageSize, FileChannel channel) {
        this.walPath = walPath;
        this.pageSize = pageSize;
        this.channel = channel;
    }

    public static WalManager open(Path walPath, int pageSize) {
        try {
            FileChannel channel = FileChannel.open(walPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE);
            WalManager wm = new WalManager(walPath, pageSize, channel);
            wm.rebuildState();
            return wm;
        } catch (IOException e) {
            throw new StorageException("Cannot open WAL file: " + walPath, e);
        }
    }

    private void rebuildState() {
        // 👇 FIXED: Added "List<WalRecord> records =" to capture the scan result
        List<WalRecord> records = scan(true, 0);
        if (!records.isEmpty()) {
            WalRecord last = records.get(records.size() - 1);
            nextLsn = last.getLsn() + 1;
            lastAppendedLsn = last.getLsn();
            lastSyncedLsn = last.getLsn();
        }
    }

    public long nextLsn() { return nextLsn; }

    public WalRecord append(WalRecordType type, long operationId, List<WalRecord.PageImage> images) {
        WalRecord record = new WalRecord(nextLsn, type, operationId, images);
        byte[] bytes = record.toBytes(pageSize);
        try {
            ByteBuffer buf = ByteBuffer.wrap(bytes);
            channel.position(writePosition);
            while (buf.hasRemaining()) channel.write(buf);
            writePosition += bytes.length;
            nextLsn++;
            lastAppendedLsn = record.getLsn();
            return record;
        } catch (IOException e) {
            throw new StorageException("Cannot append WAL record", e);
        }
    }

    public void sync() {
        try {
            channel.force(true);
            lastSyncedLsn = lastAppendedLsn;
        } catch (IOException e) {
            throw new StorageException("Cannot sync WAL", e);
        }
    }

    /** WAL-before-data rule: make sure log is durable up to this LSN. */
    public void flushToLsn(long lsn) {
        if (lsn > lastSyncedLsn) sync();
    }

    public boolean isDurable(long lsn) {
        return lsn <= lastSyncedLsn;
    }

    public List<WalRecord> readAllRecords() {
        return scan(false, 0);
    }

    private List<WalRecord> scan(boolean truncateTail, long startPos) {
        List<WalRecord> out = new ArrayList<>();
        long pos = startPos;
        try {
            long size = channel.size();
            while (pos < size) {
                ByteBuffer header = ByteBuffer.allocate(WalRecord.HEADER_SIZE);
                channel.position(pos);
                if (!readFully(header)) break;

                int magic = header.getInt(0);
                int length = header.getInt(4);
                if (magic != WalRecord.WAL_MAGIC) break;
                if (length < WalRecord.HEADER_SIZE + WalRecord.FOOTER_SIZE) break;
                if (pos + length > size) break; // incomplete record = crash mid-write

                ByteBuffer full = ByteBuffer.allocate(length);
                channel.position(pos);
                if (!readFully(full)) break;

                try {
                    out.add(WalRecord.fromBytes(full.array(), pageSize));
                } catch (StorageException e) {
                    break; // corrupted record = end of trusted region
                }
                pos += length;
            }
            if (truncateTail && pos < size) {
                channel.truncate(pos);
            }
            writePosition = pos;
        } catch (IOException e) {
            throw new StorageException("Cannot scan WAL", e);
        }
        return out;
    }

    public long currentOffset() {
        return writePosition;
    }

    public List<WalRecord> readRecordsFrom(long offset) {
        return scan(false, offset);
    }

    private boolean readFully(ByteBuffer buf) throws IOException {
        while (buf.hasRemaining()) {
            if (channel.read(buf) == -1) return false;
        }
        return true;
    }

    @Override
    public void close() {
        try {
            channel.close();
        } catch (IOException e) {
            throw new StorageException("Cannot close WAL", e);
        }
    }
}