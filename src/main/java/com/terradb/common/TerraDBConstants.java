package com.terradb.common;

public final class TerraDBConstants {

    public static final int PAGE_SIZE = 4096;
    public static final int PAGE_HEADER_SIZE = 40;
    public static final int PAGE_PAYLOAD_SIZE = PAGE_SIZE - PAGE_HEADER_SIZE;

    public static final int PAGE_MAGIC = 0x54455241; // "TERA"

    public static final int INVALID_PAGE_ID = -1;
    public static final long INVALID_LSN = -1L;

    // Page header offsets (in bytes)
    public static final int OFFSET_MAGIC = 0;
    public static final int OFFSET_PAGE_ID = 4;
    public static final int OFFSET_PAGE_TYPE = 8;
    public static final int OFFSET_FLAGS = 9;
    public static final int OFFSET_RESERVED = 10;
    public static final int OFFSET_PAGE_LSN = 12;
    public static final int OFFSET_PAYLOAD_USED = 20;
    public static final int OFFSET_ITEM_COUNT = 24;
    public static final int OFFSET_CHECKSUM = 28;

    private TerraDBConstants() {
    }
}