package com.terradb.page;

import com.terradb.common.ByteUtils;
import com.terradb.common.TerraDBConstants;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Page {

    private final int pageSize;
    private final byte[] payload;

    private int pageId;
    private PageType pageType;
    private byte flags;
    private long pageLsn;
    private int payloadUsed;
    private int itemCount;

    public Page(int pageId, PageType pageType, int pageSize) {
        this.pageSize = pageSize;
        this.pageId = pageId;
        this.pageType = pageType;
        this.flags = 0;
        this.pageLsn = TerraDBConstants.INVALID_LSN;
        this.payloadUsed = 0;
        this.itemCount = 0;
        this.payload = new byte[pageSize - TerraDBConstants.PAGE_HEADER_SIZE];
    }

    public int getPageId() { return pageId; }
    public void setPageId(int pageId) { this.pageId = pageId; }

    public PageType getPageType() { return pageType; }
    public void setPageType(PageType pageType) { this.pageType = pageType; }

    public long getPageLsn() { return pageLsn; }
    public void setPageLsn(long pageLsn) { this.pageLsn = pageLsn; }

    public int getPayloadUsed() { return payloadUsed; }
    public void setPayloadUsed(int payloadUsed) { this.payloadUsed = payloadUsed; }

    public int getItemCount() { return itemCount; }
    public void setItemCount(int itemCount) { this.itemCount = itemCount; }

    public int getPageSize() { return pageSize; }
    public byte[] getPayload() { return payload; }

    public void putPayloadInt(int offset, int value) {
        ByteBuffer buf = ByteBuffer.wrap(payload);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.putInt(offset, value);
    }

    public int getPayloadInt(int offset) {
        ByteBuffer buf = ByteBuffer.wrap(payload);
        buf.order(ByteOrder.BIG_ENDIAN);
        return buf.getInt(offset);
    }

    public void putPayloadLong(int offset, long value) {
        ByteBuffer buf = ByteBuffer.wrap(payload);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.putLong(offset, value);
    }

    public long getPayloadLong(int offset) {
        ByteBuffer buf = ByteBuffer.wrap(payload);
        buf.order(ByteOrder.BIG_ENDIAN);
        return buf.getLong(offset);
    }

    /**
     * Serialize this page into exactly pageSize bytes.
     */
    public byte[] toBytes() {
        ByteBuffer buf = ByteBuffer.allocate(pageSize);
        buf.order(ByteOrder.BIG_ENDIAN);

        buf.putInt(TerraDBConstants.OFFSET_MAGIC, TerraDBConstants.PAGE_MAGIC);
        buf.putInt(TerraDBConstants.OFFSET_PAGE_ID, pageId);
        buf.put(TerraDBConstants.OFFSET_PAGE_TYPE, pageType.code());
        buf.put(TerraDBConstants.OFFSET_FLAGS, flags);
        buf.putLong(TerraDBConstants.OFFSET_PAGE_LSN, pageLsn);
        buf.putInt(TerraDBConstants.OFFSET_PAYLOAD_USED, payloadUsed);
        buf.putInt(TerraDBConstants.OFFSET_ITEM_COUNT, itemCount);
        buf.putInt(TerraDBConstants.OFFSET_CHECKSUM, 0);

        // 👇 THIS IS THE MAGIC LINE I FORGOT! It puts your numbers in the box.
        System.arraycopy(payload, 0, buf.array(), TerraDBConstants.PAGE_HEADER_SIZE, payload.length);

        int checksum = ByteUtils.crc32(buf.array());
        buf.putInt(TerraDBConstants.OFFSET_CHECKSUM, checksum);

        return buf.array();
    }

    /**
     * Deserialize pageSize bytes into a Page.
     * Throws CorruptedPageException if magic or checksum is wrong.
     */
    public static Page fromBytes(byte[] bytes) {
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        buf.order(ByteOrder.BIG_ENDIAN);

        int magic = buf.getInt(TerraDBConstants.OFFSET_MAGIC);
        if (magic != TerraDBConstants.PAGE_MAGIC) {
            throw new CorruptedPageException("Bad page magic: " + Integer.toHexString(magic));
        }

        int pageId = buf.getInt(TerraDBConstants.OFFSET_PAGE_ID);
        PageType pageType = PageType.fromCode(buf.get(TerraDBConstants.OFFSET_PAGE_TYPE));
        byte flags = buf.get(TerraDBConstants.OFFSET_FLAGS);
        long pageLsn = buf.getLong(TerraDBConstants.OFFSET_PAGE_LSN);
        int payloadUsed = buf.getInt(TerraDBConstants.OFFSET_PAYLOAD_USED);
        int itemCount = buf.getInt(TerraDBConstants.OFFSET_ITEM_COUNT);
        int storedChecksum = buf.getInt(TerraDBConstants.OFFSET_CHECKSUM);

        byte[] copy = bytes.clone();
        ByteBuffer tmp = ByteBuffer.wrap(copy);
        tmp.order(ByteOrder.BIG_ENDIAN);
        tmp.putInt(TerraDBConstants.OFFSET_CHECKSUM, 0);
        int actualChecksum = ByteUtils.crc32(copy);

        if (actualChecksum != storedChecksum) {
            throw new CorruptedPageException(
                    "Checksum mismatch on page " + pageId
                            + " (stored=" + storedChecksum
                            + ", actual=" + actualChecksum + ")");
        }

        Page page = new Page(pageId, pageType, bytes.length);
        page.flags = flags;
        page.pageLsn = pageLsn;
        page.payloadUsed = payloadUsed;
        page.itemCount = itemCount;
        System.arraycopy(bytes, TerraDBConstants.PAGE_HEADER_SIZE,
                page.payload, 0, page.payload.length);
        return page;
    }
}