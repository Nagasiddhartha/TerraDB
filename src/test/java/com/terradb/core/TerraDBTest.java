package com.terradb.core;

import com.terradb.common.TerraDBConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Random;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

class TerraDBTest {
    @TempDir Path tempDir;
    private static final int PS = TerraDBConstants.PAGE_SIZE;

    @Test
    void testCommittedDataSurvivesCrash() {
        Path dir = tempDir.resolve("db1");
        TreeMap<Long, Long> expected = new TreeMap<>();

        TerraDB db = TerraDB.open(dir, PS, 10);
        Random rnd = new Random(1);
        for (int i = 0; i < 500; i++) {
            long k = rnd.nextInt(100000);
            long v = rnd.nextLong();
            db.put(k, v);
            expected.put(k, v);
        }
        db.simulateCrash();   // dirty buffer pages are LOST, WAL survives

        try (TerraDB db2 = TerraDB.open(dir, PS, 10)) {  // open() runs recovery
            for (var e : expected.entrySet()) {
                assertEquals(e.getValue(), db2.get(e.getKey()),
                        "committed key lost after crash: " + e.getKey());
            }
        }
    }

    @Test
    void testRecoveryIsIdempotent() {
        Path dir = tempDir.resolve("db2");
        TerraDB db = TerraDB.open(dir, PS, 10);
        for (long k = 0; k < 200; k++) db.put(k, k * 7);
        db.simulateCrash();

        try (TerraDB a = TerraDB.open(dir, PS, 10)) {
            // 199 * 7 = 1393. The database was right all along!
            assertEquals(1393L, a.get(199));
        }
        try (TerraDB b = TerraDB.open(dir, PS, 10)) {   // recover AGAIN
            assertEquals(1393L, b.get(199));
            assertEquals(0L, b.get(0));
        }
    }

    @Test
    void testCleanCloseAndReopen() {
        Path dir = tempDir.resolve("db3");
        try (TerraDB db = TerraDB.open(dir, PS, 10)) {
            for (long k = 0; k < 300; k++) db.put(k, k + 1);
            db.delete(5);
        }
        try (TerraDB db = TerraDB.open(dir, PS, 10)) {
            assertNull(db.get(5));
            // The last key inserted was 299, and its value is 299 + 1 = 300.
            assertEquals(300L, db.get(299));
        }
    }
}