package com.terradb.page;

import com.terradb.common.TerraDBConstants;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PageTest {

    @Test
    void testPageRoundTrip() {
        // 1. Create a page and put data in it
        Page page = new Page(42, PageType.BTREE_LEAF, TerraDBConstants.PAGE_SIZE);
        page.putPayloadInt(0, 999);
        page.putPayloadLong(4, 123456789L);
        page.setPayloadUsed(12);
        page.setItemCount(2);

        // 2. Turn it into bytes and read it back
        byte[] bytes = page.toBytes();
        Page copy = Page.fromBytes(bytes);

        // 3. Check if the data survived
        assertEquals(42, copy.getPageId());
        assertEquals(PageType.BTREE_LEAF, copy.getPageType());
        assertEquals(999, copy.getPayloadInt(0));
        assertEquals(123456789L, copy.getPayloadLong(4));
        assertEquals(12, copy.getPayloadUsed());
        assertEquals(2, copy.getItemCount());
    }

    @Test
    void testCorruptionDetection() {
        Page page = new Page(1, PageType.META, TerraDBConstants.PAGE_SIZE);
        byte[] bytes = page.toBytes();

        // Secretly change one byte (simulate a disk error or hacker)
        bytes[100] = (byte) (bytes[100] + 1);

        // The database MUST throw an error when reading this
        assertThrows(CorruptedPageException.class, () -> {
            Page.fromBytes(bytes);
        });
    }
}