package com.terradb.core;

import com.terradb.common.TerraDBConstants;

import java.nio.file.Path;

public class CrashWorker {
    public static void main(String[] args) {
        Path dir = Path.of(args[0]);
        int pageSize = Integer.parseInt(args[1]);
        CrashPoint point = CrashPoint.valueOf(args[2]);

        TerraDB db = TerraDB.open(dir, pageSize, 10);

        for (long k = 1; k <= 100; k++) {   // 100 committed keys
            db.put(k, k * 10);
        }

        db.putWithCrash(999, 999, point);   // the operation that dies mid-way

        Runtime.getRuntime().halt(0);
    }
}