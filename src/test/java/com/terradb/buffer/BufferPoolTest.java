package com.terradb.buffer;

import com.terradb.common.TerraDBConstants;
import com.terradb.disk.DiskManager;
import com.terradb.page.Page;
import com.terradb.page.PageType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class BufferPoolTest {
    @TempDir Path tempDir;

    @Test
    void testCacheHitAndMiss() {
        Path db = tempDir.resolve("bp.db");
        try (DiskManager disk = DiskManager.open(db, TerraDBConstants.PAGE_SIZE)) {
            Page p1 = disk.allocatePage(PageType.BTREE_LEAF);
            disk.writePage(p1);

            BufferPool bp = new BufferPool(disk, 2);
            bp.pin(p1.getPageId()); // Miss
            bp.unpin(p1, false);
            bp.pin(p1.getPageId()); // Hit!

            assertEquals(1, bp.metrics.misses);
            assertEquals(1, bp.metrics.hits);
        }
    }

    @Test
    void testPinnedPageCannotBeEvicted() {
        Path db = tempDir.resolve("bp2.db");
        try (DiskManager disk = DiskManager.open(db, TerraDBConstants.PAGE_SIZE)) {
            Page p1 = disk.allocatePage(PageType.BTREE_LEAF); disk.writePage(p1);
            Page p2 = disk.allocatePage(PageType.BTREE_LEAF); disk.writePage(p2);

            BufferPool bp = new BufferPool(disk, 1); // Desk has only 1 slot!
            bp.pin(p1.getPageId());
            // p1 is pinned. If we try to load p2, it should crash.
            assertThrows(BufferPoolFullException.class, () -> bp.pin(p2.getPageId()));
        }
    }
}