package com.terradb.btree;

import com.terradb.buffer.BufferPool;
import com.terradb.common.TerraDBConstants;
import com.terradb.disk.DiskManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Random;
import java.util.TreeMap;
import static org.junit.jupiter.api.Assertions.*;

class BTreeTest {
    @TempDir Path tempDir;

    @Test
    void testInsertAndGet() {
        Path db = tempDir.resolve("btree.db");
        try (DiskManager disk = DiskManager.open(db, TerraDBConstants.PAGE_SIZE)) {
            BufferPool bp = new BufferPool(disk, 10);
            BTree tree = new BTree(disk, bp);

            tree.put(10, 100);
            tree.put(5, 50);
            tree.put(15, 150);

            assertEquals(100, tree.get(10));
            assertEquals(50, tree.get(5));
            assertEquals(150, tree.get(15));
            assertNull(tree.get(99));

            bp.flushAll();
        }
    }

    @Test
    void testMassiveInsertWithSplits() {
        Path db = tempDir.resolve("btree_massive.db");
        try (DiskManager disk = DiskManager.open(db, TerraDBConstants.PAGE_SIZE)) {
            BufferPool bp = new BufferPool(disk, 5);
            BTree tree = new BTree(disk, bp);
            TreeMap<Long, Long> map = new TreeMap<>();
            Random rnd = new Random(42);

            for (int i = 0; i < 2000; i++) {
                long k = rnd.nextInt(100000);
                long v = rnd.nextLong();
                tree.put(k, v);
                map.put(k, v);
            }
            bp.flushAll();

            for (long k : map.keySet()) {
                assertEquals(map.get(k), tree.get(k), "Mismatch on key " + k);
            }
        }
    }
}