package com.terradb.disk;

import com.terradb.common.TerraDBConstants;
import com.terradb.page.Page;
import com.terradb.page.PageType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DiskManagerTest {

    // This creates a temporary folder that deletes itself after the test!
    @TempDir
    Path tempDir;

    @Test
    void testCreateWriteReopenRead() {
        Path dbFile = tempDir.resolve("test.db");

        // SESSION 1: Write data and close
        try (DiskManager dm = DiskManager.open(dbFile, TerraDBConstants.PAGE_SIZE)) {
            Page p1 = dm.allocatePage(PageType.BTREE_LEAF);
            p1.putPayloadInt(0, 111);
            dm.writePage(p1);

            Page p2 = dm.allocatePage(PageType.BTREE_INTERNAL);
            p2.putPayloadInt(0, 222);
            dm.writePage(p2);

            dm.sync();
        }

        // SESSION 2: Reopen and verify data survived
        try (DiskManager dm = DiskManager.open(dbFile, TerraDBConstants.PAGE_SIZE)) {
            assertEquals(3, dm.getPageCount()); // 0=meta, 1=p1, 2=p2

            Page read1 = dm.readPage(1);
            assertEquals(111, read1.getPayloadInt(0));
            assertEquals(PageType.BTREE_LEAF, read1.getPageType());

            Page read2 = dm.readPage(2);
            assertEquals(222, read2.getPayloadInt(0));
            assertEquals(PageType.BTREE_INTERNAL, read2.getPageType());
        }
    }

    @Test
    void testFreeListReuse() {
        Path dbFile = tempDir.resolve("test_free.db");

        try (DiskManager dm = DiskManager.open(dbFile, TerraDBConstants.PAGE_SIZE)) {
            Page p1 = dm.allocatePage(PageType.BTREE_LEAF);
            int id1 = p1.getPageId(); // Should be 1
            dm.writePage(p1);

            // Delete the page
            dm.deallocatePage(id1);

            // Allocate a new page. It SHOULD reuse the deleted ID!
            Page p2 = dm.allocatePage(PageType.BTREE_LEAF);
            assertEquals(id1, p2.getPageId());
        }
    }
}