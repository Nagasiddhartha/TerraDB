package com.terradb.btree;

import com.terradb.buffer.BufferPool;
import com.terradb.common.TerraDBConstants;
import com.terradb.disk.DiskManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

class BTreeDeleteScanTest {
    @TempDir Path tempDir;

    @Test
    void testDeleteMatchesTreeMap() {
        Path db = tempDir.resolve("del.db");
        try (DiskManager disk = DiskManager.open(db, TerraDBConstants.PAGE_SIZE)) {
            BufferPool bp = new BufferPool(disk, 20);
            BTree tree = new BTree(disk, bp);
            TreeMap<Long, Long> map = new TreeMap<>();
            Random rnd = new Random(7);

            for (int i = 0; i < 3000; i++) {
                long k = rnd.nextInt(50000);
                long v = rnd.nextLong();
                tree.put(k, v);
                map.put(k, v);
            }
            for (int i = 0; i < 1500; i++) {
                long k = rnd.nextInt(50000);
                boolean expected = map.remove(k) != null;
                boolean actual = tree.delete(k);
                assertEquals(expected, actual, "delete mismatch on key " + k);
            }
            bp.flushAll();
            for (long k : map.keySet()) {
                assertEquals(map.get(k), tree.get(k), "get mismatch on key " + k);
            }
        }
    }

    @Test
    void testRangeScanMatchesTreeMap() {
        Path db = tempDir.resolve("scan.db");
        try (DiskManager disk = DiskManager.open(db, TerraDBConstants.PAGE_SIZE)) {
            BufferPool bp = new BufferPool(disk, 20);
            BTree tree = new BTree(disk, bp);
            TreeMap<Long, Long> map = new TreeMap<>();
            Random rnd = new Random(99);

            for (int i = 0; i < 2000; i++) {
                long k = rnd.nextInt(100000);
                long v = rnd.nextLong();
                tree.put(k, v);
                map.put(k, v);
            }

            List<KV> got = tree.scan(10000, 20000);
            List<long[]> want = new ArrayList<>();
            for (var e : map.subMap(10000L, true, 20000L, true).entrySet()) {
                want.add(new long[]{e.getKey(), e.getValue()});
            }
            assertEquals(want.size(), got.size());
            for (int i = 0; i < want.size(); i++) {
                assertEquals(want.get(i)[0], got.get(i).key());
                assertEquals(want.get(i)[1], got.get(i).value());
            }
        }
    }

    @Test
    void testDeleteEverything() {
        Path db = tempDir.resolve("empty.db");
        try (DiskManager disk = DiskManager.open(db, TerraDBConstants.PAGE_SIZE)) {
            BufferPool bp = new BufferPool(disk, 20);
            BTree tree = new BTree(disk, bp);
            for (long k = 0; k < 500; k++) tree.put(k, k * 10);
            for (long k = 0; k < 500; k++) assertTrue(tree.delete(k));
            bp.flushAll();
            for (long k = 0; k < 500; k++) assertNull(tree.get(k));
            assertTrue(tree.scan(Long.MIN_VALUE, Long.MAX_VALUE).isEmpty());
        }
    }
}