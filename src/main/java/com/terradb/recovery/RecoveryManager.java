package com.terradb.recovery;

import com.terradb.disk.DiskManager;
import com.terradb.wal.WalManager;
import com.terradb.wal.WalRecord;
import com.terradb.wal.WalRecordType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class RecoveryManager {

    private RecoveryManager() {
    }

    public static int recover(Path dbPath, Path walPath, int pageSize) {
        return recoverFromCheckpoint(dbPath, walPath, null, pageSize);
    }

    /**
     * Redo all committed operations after the given checkpoint.
     * If ckptPath is null (or unreadable), scan the whole WAL.
     */
    public static int recoverFromCheckpoint(Path dbPath, Path walPath,
                                            Path ckptPath, int pageSize) {
        if (!Files.exists(walPath)) return 0;

        CheckpointInfo ck = (ckptPath == null) ? null : CheckpointFile.tryRead(ckptPath);

        try (WalManager wal = WalManager.open(walPath, pageSize);
             DiskManager disk = DiskManager.open(dbPath, pageSize)) {

            List<WalRecord> records = (ck == null)
                    ? wal.readAllRecords()
                    : wal.readRecordsFrom(ck.offset());

            int redone = 0;
            for (WalRecord r : records) {
                if (r.getType() != WalRecordType.OPERATION_COMMIT) continue;
                for (WalRecord.PageImage img : r.getImages()) {
                    disk.writeRawPage(img.pageId(), img.image());
                }
                redone++;
            }
            disk.sync();
            disk.reloadMetaPage();
            return redone;
        }
    }
}