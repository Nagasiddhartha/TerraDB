package com.terradb.wal;

import com.terradb.common.ByteUtils;
import com.terradb.common.StorageException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class WalRecord {

    public static final int WAL_MAGIC = 0x57414C31; // "WAL1"
    public static final int HEADER_SIZE = 32;
    public static final int FOOTER_SIZE = 4;

    public record PageImage(int pageId, byte[] image) {
    }

    private final long lsn;
    private final WalRecordType type;
    private final long operationId;
    private final List<PageImage> images;

    public WalRecord(long lsn, WalRecordType type, long operationId, List<PageImage> images) {
        this.lsn = lsn;
        this.type = type;
        this.operationId = operationId;
        this.images = images;
    }

    public long getLsn() { return lsn; }
    public WalRecordType getType() { return type; }
    public long getOperationId() { return operationId; }
    public List<PageImage> getImages() { return images; }

    public byte[] toBytes(int pageSize) {
        int total = HEADER_SIZE + images.size() * (4 + pageSize) + FOOTER_SIZE;
        ByteBuffer buf = ByteBuffer.allocate(total);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.putInt(0, WAL_MAGIC);
        buf.putInt(4, total);
        buf.putLong(8, lsn);
        buf.putInt(16, type.code());
        buf.putLong(20, operationId);
        buf.putInt(28, images.size());

        int pos = HEADER_SIZE;
        for (PageImage img : images) {
            buf.putInt(pos, img.pageId());
            System.arraycopy(img.image(), 0, buf.array(), pos + 4, pageSize);
            pos += 4 + pageSize;
        }

        int crc = ByteUtils.crc32(buf.array());
        buf.putInt(total - FOOTER_SIZE, crc);
        return buf.array();
    }

    public static WalRecord fromBytes(byte[] bytes, int pageSize) {
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        buf.order(ByteOrder.BIG_ENDIAN);

        if (buf.getInt(0) != WAL_MAGIC) {
            throw new StorageException("Bad WAL magic");
        }
        int length = buf.getInt(4);
        if (length != bytes.length) {
            throw new StorageException("WAL record length mismatch");
        }

        long lsn = buf.getLong(8);
        WalRecordType type = WalRecordType.fromCode(buf.getInt(16));
        long operationId = buf.getLong(20);
        int count = buf.getInt(28);
        int storedChecksum = buf.getInt(bytes.length - FOOTER_SIZE);

        byte[] copy = bytes.clone();
        for (int i = copy.length - FOOTER_SIZE; i < copy.length; i++) copy[i] = 0;
        int actualChecksum = ByteUtils.crc32(copy);
        if (actualChecksum != storedChecksum) {
            throw new StorageException("WAL checksum mismatch on LSN " + lsn);
        }

        List<PageImage> images = new ArrayList<>();
        int pos = HEADER_SIZE;
        for (int i = 0; i < count; i++) {
            int pageId = buf.getInt(pos);
            byte[] img = new byte[pageSize];
            System.arraycopy(bytes, pos + 4, img, 0, pageSize);
            images.add(new PageImage(pageId, img));
            pos += 4 + pageSize;
        }
        return new WalRecord(lsn, type, operationId, images);
    }
}