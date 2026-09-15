package com.terradb.core;

import com.terradb.common.TerraDBConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CrashInjectionTest {
    @TempDir Path tempDir;

    private void runCrashProcess(Path dir, CrashPoint point) throws Exception {
        String javaBin = System.getProperty("java.home") + File.separator
                + "bin" + File.separator + "java";
        String cp = System.getProperty("java.class.path");

        ProcessBuilder pb = new ProcessBuilder(
                javaBin, "-cp", cp,
                "com.terradb.core.CrashWorker",
                dir.toString(),
                String.valueOf(TerraDBConstants.PAGE_SIZE),
                point.name());
        pb.inheritIO();
        Process p = pb.start();
        int code = p.waitFor();
        assertEquals(7, code, "worker must halt(7) at point " + point);
    }

    @Test
    void testAllSixCrashPoints() throws Exception {
        for (CrashPoint point : CrashPoint.values()) {
            if (point == CrashPoint.NONE) continue;

            Path dir = tempDir.resolve("crash_" + point.name());
            runCrashProcess(dir, point);

            try (TerraDB db = TerraDB.open(dir, TerraDBConstants.PAGE_SIZE, 10)) {
                for (long k = 1; k <= 100; k++) {
                    assertEquals(k * 10, db.get(k),
                            "committed key lost after crash at " + point);
                }

                Long v = db.get(999);
                boolean expectPresent =
                        point == CrashPoint.AFTER_WAL_APPEND
                                || point == CrashPoint.AFTER_WAL_FSYNC
                                || point == CrashPoint.AFTER_DATA_FLUSH
                                || point == CrashPoint.BEFORE_CHECKPOINT_COMPLETE;

                if (expectPresent) {
                    assertEquals(999L, v, "crashed op should be redone at " + point);
                } else {
                    assertNull(v, "uncommitted op must be absent at " + point);
                }
            }
        }
    }
}