package com.terradb.core;

import com.terradb.common.TerraDBConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CheckpointRecoveryTest {
    @TempDir Path tempDir;
    private static final int PS = TerraDBConstants.PAGE_SIZE;

    @Test
    void testCheckpointThenCrashThenRecover() {
        Path dir = tempDir.resolve("ckpt");

        TerraDB db = TerraDB.open(dir, PS, 10);
        for (long k = 1; k <= 200; k++) db.put(k, k);
        db.checkpoint();
        assertTrue(Files.exists(dir.resolve("terradb.ckpt")));

        for (long k = 201; k <= 300; k++) db.put(k, k);
        db.simulateCrash();

        try (TerraDB db2 = TerraDB.open(dir, PS, 10)) {
            for (long k = 1; k <= 300; k++) {
                assertEquals(k, db2.get(k), "key lost after checkpoint recovery: " + k);
            }
        }
    }
}