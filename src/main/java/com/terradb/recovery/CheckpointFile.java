package com.terradb.recovery;

import com.terradb.common.ByteUtils;
import com.terradb.common.StorageException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class CheckpointFile {

    public static final int CKPT_MAGIC = 0x434B5054; // "CKPT"

    private CheckpointFile() {
    }

    public static void write(Path path, CheckpointInfo info) {
        ByteBuffer buf = ByteBuffer.allocate(24);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.putInt(0, CKPT_MAGIC);
        buf.putLong(4, info.lsn());
        buf.putLong(12, info.offset());
        int crc = ByteUtils.crc32(buf.array());
        buf.putInt(20, crc);
        try (FileChannel ch = FileChannel.open(path,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            ch.position(0);
            while (buf.hasRemaining()) ch.write(buf);
            ch.force(true);
        } catch (IOException e) {
            throw new StorageException("Cannot write checkpoint file", e);
        }
    }

    public static CheckpointInfo tryRead(Path path) {
        if (!Files.exists(path)) return null;
        try (FileChannel ch = FileChannel.open(path, StandardOpenOption.READ)) {
            if (ch.size() < 24) return null;
            ByteBuffer buf = ByteBuffer.allocate(24);
            while (buf.hasRemaining()) {
                if (ch.read(buf) == -1) return null;
            }
            int magic = buf.getInt(0);
            long lsn = buf.getLong(4);
            long offset = buf.getLong(12);
            int stored = buf.getInt(20);
            byte[] copy = buf.array().clone();
            for (int i = 20; i < 24; i++) copy[i] = 0;
            if (magic != CKPT_MAGIC || ByteUtils.crc32(copy) != stored) return null;
            return new CheckpointInfo(lsn, offset);
        } catch (IOException e) {
            return null;
        }
    }
}