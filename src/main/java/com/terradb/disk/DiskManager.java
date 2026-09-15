package com.terradb.disk;

import com.terradb.common.StorageException;
import com.terradb.common.TerraDBConstants;
import com.terradb.page.Page;
import com.terradb.page.PageType;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class DiskManager implements AutoCloseable {

    public static final int META_ROOT_PAGE_ID = 0;
    public static final int META_FIRST_FREE_PAGE_ID = 4;
    public static final int META_NEXT_PAGE_ID = 8;
    public static final int META_PAGE_SIZE = 12;

    private final Path dbPath;
    private final int pageSize;
    private final FileChannel channel;

    private int rootPageId;
    private int firstFreePageId;
    private int nextPageId;

    private DiskManager(Path dbPath, int pageSize, FileChannel channel,
                        int rootPageId, int firstFreePageId, int nextPageId) {
        this.dbPath = dbPath;
        this.pageSize = pageSize;
        this.channel = channel;
        this.rootPageId = rootPageId;
        this.firstFreePageId = firstFreePageId;
        this.nextPageId = nextPageId;
    }

    public static DiskManager open(Path dbPath, int pageSize) {
        try {
            boolean isNew = !Files.exists(dbPath) || Files.size(dbPath) == 0;

            FileChannel channel = FileChannel.open(dbPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE);

            if (isNew) {
                DiskManager dm = new DiskManager(dbPath, pageSize, channel,
                        TerraDBConstants.INVALID_PAGE_ID,
                        TerraDBConstants.INVALID_PAGE_ID,
                        1);
                dm.writeMetaPage();
                dm.sync();
                return dm;
            }

            ByteBuffer buf = ByteBuffer.allocate(pageSize);
            readFully(channel, buf, 0);
            Page meta = Page.fromBytes(buf.array());

            if (meta.getPageType() != PageType.META) {
                throw new StorageException("Page 0 is not a META page");
            }

            int root = meta.getPayloadInt(META_ROOT_PAGE_ID);
            int firstFree = meta.getPayloadInt(META_FIRST_FREE_PAGE_ID);
            int nextPage = meta.getPayloadInt(META_NEXT_PAGE_ID);
            int storedPageSize = meta.getPayloadInt(META_PAGE_SIZE);

            if (storedPageSize != pageSize) {
                throw new StorageException("Page size mismatch: file="
                        + storedPageSize + ", requested=" + pageSize);
            }

            return new DiskManager(dbPath, pageSize, channel, root, firstFree, nextPage);
        } catch (IOException e) {
            throw new StorageException("Cannot open database file: " + dbPath, e);
        }
    }

    public Page allocatePage(PageType type) {
        if (firstFreePageId != TerraDBConstants.INVALID_PAGE_ID) {
            int reusedId = firstFreePageId;
            Page freePage = readPage(reusedId);
            firstFreePageId = freePage.getPayloadInt(0);
            writeMetaPage();
            return new Page(reusedId, type, pageSize);
        }
        int newId = nextPageId;
        nextPageId++;
        writeMetaPage();
        return new Page(newId, type, pageSize);
    }

    public void deallocatePage(int pageId) {
        Page freePage = new Page(pageId, PageType.FREE_LIST, pageSize);
        freePage.putPayloadInt(0, firstFreePageId);
        firstFreePageId = pageId;
        writePage(freePage);   // image FIRST
        writeMetaPage();       // link SECOND (crash-safe order)
    }

    public Page readPage(int pageId) {
        try {
            ByteBuffer buf = ByteBuffer.allocate(pageSize);
            readFully(channel, buf, (long) pageId * pageSize);
            return Page.fromBytes(buf.array());
        } catch (IOException e) {
            throw new StorageException("Cannot read page " + pageId, e);
        }
    }

    public void writePage(Page page) {
        try {
            ByteBuffer buf = ByteBuffer.wrap(page.toBytes());
            long offset = (long) page.getPageId() * pageSize;
            channel.position(offset);
            while (buf.hasRemaining()) channel.write(buf);
        } catch (IOException e) {
            throw new StorageException("Cannot write page " + page.getPageId(), e);
        }
    }

    /** Write already-serialized bytes (used by recovery redo). */
    public void writeRawPage(int pageId, byte[] bytes) {
        try {
            ByteBuffer buf = ByteBuffer.wrap(bytes);
            long offset = (long) pageId * pageSize;
            channel.position(offset);
            while (buf.hasRemaining()) channel.write(buf);
        } catch (IOException e) {
            throw new StorageException("Cannot write raw page " + pageId, e);
        }
    }

    public void sync() {
        try {
            channel.force(true);
        } catch (IOException e) {
            throw new StorageException("Cannot sync database file", e);
        }
    }
    /**
     * Reloads the meta page from disk into memory.
     * Used by recovery after writing raw pages to disk.
     */
    public void reloadMetaPage() {
        try {
            ByteBuffer buf = ByteBuffer.allocate(pageSize);
            readFully(channel, buf, 0);
            Page meta = Page.fromBytes(buf.array());
            this.rootPageId = meta.getPayloadInt(META_ROOT_PAGE_ID);
            this.firstFreePageId = meta.getPayloadInt(META_FIRST_FREE_PAGE_ID);
            this.nextPageId = meta.getPayloadInt(META_NEXT_PAGE_ID);
        } catch (IOException e) {
            throw new StorageException("Cannot reload meta page", e);
        }
    }
    @Override
    public void close() {
        try {
            writeMetaPage();
            channel.force(true);
            channel.close();
        } catch (IOException e) {
            throw new StorageException("Cannot close database file", e);
        }
    }

    /** Close like a crash: no meta write, no flush. Memory is lost. */
    public void abortClose() {
        try {
            channel.close();
        } catch (IOException e) {
            throw new StorageException("Cannot abort-close database file", e);
        }
    }

    public int getRootPageId() { return rootPageId; }
    public void setRootPageId(int rootPageId) { this.rootPageId = rootPageId; }
    public int getFirstFreePageId() { return firstFreePageId; }
    public int getPageCount() { return nextPageId; }
    public Path getDbPath() { return dbPath; }

    /** Build the meta page in memory WITHOUT writing it to disk. */
    public Page buildMetaPage() {
        Page meta = new Page(0, PageType.META, pageSize);
        meta.putPayloadInt(META_ROOT_PAGE_ID, rootPageId);
        meta.putPayloadInt(META_FIRST_FREE_PAGE_ID, firstFreePageId);
        meta.putPayloadInt(META_NEXT_PAGE_ID, nextPageId);
        meta.putPayloadInt(META_PAGE_SIZE, pageSize);
        return meta;
    }

    private void writeMetaPage() {
        writePage(buildMetaPage());
    }

    private static void readFully(FileChannel channel, ByteBuffer buf, long position)
            throws IOException {
        channel.position(position);
        while (buf.hasRemaining()) {
            if (channel.read(buf) == -1) {
                throw new StorageException(
                        "Unexpected end of database file at position " + position);
            }
        }
    }
}