package com.terradb.common;

import java.util.zip.CRC32;

public final class ByteUtils {

    private ByteUtils() {
    }

    /**
     * CRC32 checksum over the whole byte array.
     * The caller must zero the checksum field before calling this.
     */
    public static int crc32(byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(data, 0, data.length);
        return (int) crc.getValue();
    }
}