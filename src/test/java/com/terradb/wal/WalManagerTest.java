package com.terradb.wal;

import com.terradb.common.TerraDBConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class WalManagerTest {
    @TempDir Path tempDir;
    private static final int PS = TerraDBConstants.PAGE_SIZE;

    private List<WalRecord.PageImage> fakeImage(int pageId, int seed) {
        byte[] img = new byte[PS];
        new Random(seed).nextBytes(img);
        return List.of(new WalRecord.PageImage(pageId, img));
    }

    @Test
    void testAppendReadBackAndMonotonicLsn() {
        Path wal = tempDir.resolve("test.wal");
        try (WalManager wm = WalManager.open(wal, PS)) {
            WalRecord r1 = wm.append(WalRecordType.OPERATION_COMMIT, 1, fakeImage(1, 1));
            WalRecord r2 = wm.append(WalRecordType.OPERATION_COMMIT, 2, fakeImage(2, 2));
            WalRecord r3 = wm.append(WalRecordType.OPERATION_COMMIT, 3, fakeImage(3, 3));
            wm.sync();

            assertEquals(1, r1.getLsn());
            assertEquals(2, r2.getLsn());
            assertEquals(3, r3.getLsn());

            List<WalRecord> all = wm.readAllRecords();
            assertEquals(3, all.size());
            assertArrayEquals(fakeImage(2, 2).get(0).image(),
                    all.get(1).getImages().get(0).image());
        }
        try (WalManager wm = WalManager.open(wal, PS)) {
            assertEquals(4, wm.nextLsn());
            assertEquals(3, wm.readAllRecords().size());
        }
    }

    @Test
    void testWalBeforeDataRule() {
        Path wal = tempDir.resolve("dur.wal");
        try (WalManager wm = WalManager.open(wal, PS)) {
            WalRecord r = wm.append(WalRecordType.OPERATION_COMMIT, 9, fakeImage(5, 5));
            assertFalse(wm.isDurable(r.getLsn()));  // diary written but NOT saved yet
            wm.flushToLsn(r.getLsn());              // NOW save diary (fsync)
            assertTrue(wm.isDurable(r.getLsn()));   // only now may data page flush
        }
    }

    @Test
    void testPartialRecordIgnoredAfterCrash() throws Exception {
        Path wal = tempDir.resolve("partial.wal");
        try (WalManager wm = WalManager.open(wal, PS)) {
            wm.append(WalRecordType.OPERATION_COMMIT, 1, fakeImage(1, 1));
            wm.append(WalRecordType.OPERATION_COMMIT, 2, fakeImage(2, 2));
            wm.sync();
        }
        long goodSize = Files.size(wal);

        try (WalManager wm = WalManager.open(wal, PS)) {
            wm.append(WalRecordType.OPERATION_COMMIT, 3, fakeImage(3, 3));
        }
        try (FileChannel ch = FileChannel.open(wal, StandardOpenOption.WRITE)) {
            ch.truncate(goodSize + 100); // simulate crash mid-write of record 3
        }

        try (WalManager wm = WalManager.open(wal, PS)) {
            assertEquals(2, wm.readAllRecords().size());
            assertEquals(3, wm.nextLsn());
        }
    }

    @Test
    void testCorruptedByteStopsRecovery() throws Exception {
        Path wal = tempDir.resolve("corrupt.wal");
        try (WalManager wm = WalManager.open(wal, PS)) {
            wm.append(WalRecordType.OPERATION_COMMIT, 1, fakeImage(1, 1));
            wm.append(WalRecordType.OPERATION_COMMIT, 2, fakeImage(2, 2));
            wm.sync();
        }
        byte[] all = Files.readAllBytes(wal);
        all[50] = (byte) (all[50] + 1); // flip one byte inside record 1
        Files.write(wal, all);

        try (WalManager wm = WalManager.open(wal, PS)) {
            assertEquals(0, wm.readAllRecords().size());
        }
    }
}