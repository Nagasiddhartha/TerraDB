package com.terradb.btree;

import com.terradb.buffer.BufferPool;
import com.terradb.common.TerraDBConstants;
import com.terradb.disk.DiskManager;
import com.terradb.page.Page;
import com.terradb.page.PageType;

import java.util.ArrayList;
import java.util.List;

public class BTree {
    private final DiskManager disk;
    private final BufferPool bufferPool;
    private int rootPageId;

    public BTree(DiskManager disk, BufferPool bufferPool) {
        this.disk = disk;
        this.bufferPool = bufferPool;
        this.rootPageId = disk.getRootPageId();
    }

    // ---------------- SEARCH ----------------
    public Long get(long key) {
        if (rootPageId == TerraDBConstants.INVALID_PAGE_ID) return null;
        Page curr = pinLeafForKey(key);
        LeafNode leaf = new LeafNode(curr);
        int idx = leaf.findKeyIndex(key);
        Long result = (idx >= 0) ? leaf.getVal(idx) : null;
        bufferPool.unpin(curr, false);
        return result;
    }

    // ---------------- INSERT ----------------
    public void put(long key, long value) {
        if (rootPageId == TerraDBConstants.INVALID_PAGE_ID) {
            Page p = disk.allocatePage(PageType.BTREE_LEAF);
            bufferPool.pinNew(p);
            LeafNode leaf = new LeafNode(p);
            leaf.initNew(TerraDBConstants.INVALID_PAGE_ID);
            leaf.insertSorted(key, value);
            rootPageId = p.getPageId();
            disk.setRootPageId(rootPageId);
            bufferPool.unpin(p, true);
            return;
        }

        Page curr = pinLeafForKey(key);
        LeafNode leaf = new LeafNode(curr);
        if (leaf.getCount() < LeafNode.MAX_ENTRIES) {
            leaf.insertSorted(key, value);
            bufferPool.unpin(curr, true);
        } else {
            splitLeaf(leaf, key, value);
        }
    }

    private void splitLeaf(LeafNode leaf, long key, long value) {
        leaf.insertSorted(key, value);
        Page newPage = disk.allocatePage(PageType.BTREE_LEAF);
        bufferPool.pinNew(newPage);
        LeafNode newLeaf = new LeafNode(newPage);
        newLeaf.initNew(leaf.getParent());

        long separator = leaf.split(newLeaf);

        newLeaf.setNext(leaf.getNext());
        newLeaf.setPrev(leaf.getPageId());
        leaf.setNext(newLeaf.getPageId());
        if (newLeaf.getNext() != TerraDBConstants.INVALID_PAGE_ID) {
            Page np = bufferPool.pin(newLeaf.getNext());
            new LeafNode(np).setPrev(newLeaf.getPageId());
            bufferPool.unpin(np, true);
        }

        insertIntoParent(leaf.getPageId(), separator, newLeaf.getPageId());
        bufferPool.unpin(newPage, true);
        bufferPool.unpin(leaf.page, true);
    }

    private void insertIntoParent(int leftId, long key, int rightId) {
        if (leftId == rootPageId) {
            Page newRootPage = disk.allocatePage(PageType.BTREE_INTERNAL);
            bufferPool.pinNew(newRootPage);
            InternalNode newRoot = new InternalNode(newRootPage);
            newRoot.initNew(TerraDBConstants.INVALID_PAGE_ID);
            newRoot.setChild(0, leftId);
            newRoot.insertSorted(key, rightId);
            rootPageId = newRootPage.getPageId();
            disk.setRootPageId(rootPageId);

            Page lp = bufferPool.pin(leftId);
            lp.putPayloadInt(0, rootPageId);
            bufferPool.unpin(lp, true);
            Page rp = bufferPool.pin(rightId);
            rp.putPayloadInt(0, rootPageId);
            bufferPool.unpin(rp, true);

            bufferPool.unpin(newRootPage, true);
            return;
        }

        Page leftPage = bufferPool.pin(leftId);
        int parentId = leftPage.getPayloadInt(0);
        bufferPool.unpin(leftPage, false);

        Page parentPage = bufferPool.pin(parentId);
        InternalNode parent = new InternalNode(parentPage);

        if (parent.getCount() < InternalNode.MAX_KEYS) {
            parent.insertSorted(key, rightId);
            Page rp = bufferPool.pin(rightId);
            rp.putPayloadInt(0, parentId);
            bufferPool.unpin(rp, true);
            bufferPool.unpin(parentPage, true);
        } else {
            splitInternal(parent, key, rightId);
        }
    }

    private void splitInternal(InternalNode node, long key, int rightId) {
        node.insertSorted(key, rightId);
        Page newPage = disk.allocatePage(PageType.BTREE_INTERNAL);
        bufferPool.pinNew(newPage);
        InternalNode newNode = new InternalNode(newPage);
        newNode.initNew(node.getParent());

        long promoteKey = node.split(newNode);

        for (int i = 0; i <= newNode.getCount(); i++) {
            int childId = newNode.getChild(i);
            Page cp = bufferPool.pin(childId);
            cp.putPayloadInt(0, newPage.getPageId());
            bufferPool.unpin(cp, true);
        }

        insertIntoParent(node.getPageId(), promoteKey, newPage.getPageId());
        bufferPool.unpin(newPage, true);
        bufferPool.unpin(node.page, true);
    }

    // ---------------- DELETE ----------------
    public boolean delete(long key) {
        if (rootPageId == TerraDBConstants.INVALID_PAGE_ID) return false;

        Page leafPage = pinLeafForKey(key);
        LeafNode leaf = new LeafNode(leafPage);
        int idx = leaf.findKeyIndex(key);
        if (idx < 0) {
            bufferPool.unpin(leafPage, false);
            return false;
        }
        leaf.removeAt(idx);

        if (leafPage.getPageId() == rootPageId) {
            bufferPool.unpin(leafPage, true);
            return true;
        }
        if (leaf.getCount() >= LeafNode.MIN_ENTRIES) {
            bufferPool.unpin(leafPage, true);
            return true;
        }

        fixLeafUnderflow(leaf, leafPage);
        bufferPool.unpin(leafPage, true); // no-op if page was freed
        return true;
    }

    private void fixLeafUnderflow(LeafNode leaf, Page leafPage) {
        int parentId = leaf.getParent();
        if (parentId == TerraDBConstants.INVALID_PAGE_ID) return;

        Page parentPage = bufferPool.pin(parentId);
        InternalNode parent = new InternalNode(parentPage);
        int idx = parent.indexOfChild(leaf.getPageId());
        if (idx < 0) { bufferPool.unpin(parentPage, false); return; }

        // borrow from LEFT
        if (idx > 0) {
            int leftId = parent.getChild(idx - 1);
            Page leftPage = bufferPool.pin(leftId);
            LeafNode left = new LeafNode(leftPage);
            if (left.getCount() > LeafNode.MIN_ENTRIES) {
                int li = left.getCount() - 1;
                leaf.prepend(left.getKey(li), left.getVal(li));
                left.removeAt(li);
                parent.setKey(idx - 1, leaf.getKey(0));
                bufferPool.unpin(leftPage, true);
                bufferPool.unpin(parentPage, true);
                return;
            }
            bufferPool.unpin(leftPage, false);
        }

        // borrow from RIGHT
        if (idx < parent.getCount()) {
            int rightId = parent.getChild(idx + 1);
            Page rightPage = bufferPool.pin(rightId);
            LeafNode right = new LeafNode(rightPage);
            if (right.getCount() > LeafNode.MIN_ENTRIES) {
                leaf.append(right.getKey(0), right.getVal(0));
                right.removeAt(0);
                parent.setKey(idx, right.getKey(0));
                bufferPool.unpin(rightPage, true);
                bufferPool.unpin(parentPage, true);
                return;
            }
            bufferPool.unpin(rightPage, false);
        }

        // merge
        if (idx > 0) {
            int leftId = parent.getChild(idx - 1);
            Page leftPage = bufferPool.pin(leftId);
            LeafNode left = new LeafNode(leftPage);
            for (int i = 0; i < leaf.getCount(); i++) {
                left.append(leaf.getKey(i), leaf.getVal(i));
            }
            left.setNext(leaf.getNext());
            if (leaf.getNext() != TerraDBConstants.INVALID_PAGE_ID) {
                Page np = bufferPool.pin(leaf.getNext());
                new LeafNode(np).setPrev(leftId);
                bufferPool.unpin(np, true);
            }
            parent.removeAt(idx - 1);
            bufferPool.unpin(leftPage, true);
            bufferPool.unpin(leafPage, false);
            freePage(leaf.getPageId());
        } else {
            int rightId = parent.getChild(idx + 1);
            Page rightPage = bufferPool.pin(rightId);
            LeafNode right = new LeafNode(rightPage);
            for (int i = 0; i < right.getCount(); i++) {
                leaf.append(right.getKey(i), right.getVal(i));
            }
            leaf.setNext(right.getNext());
            if (right.getNext() != TerraDBConstants.INVALID_PAGE_ID) {
                Page np = bufferPool.pin(right.getNext());
                new LeafNode(np).setPrev(leaf.getPageId());
                bufferPool.unpin(np, true);
            }
            parent.removeAt(idx);
            bufferPool.unpin(rightPage, false);
            freePage(rightId);
        }

        finishParentFix(parent, parentPage);
    }

    private void fixInternalUnderflow(InternalNode node, Page nodePage) {
        int parentId = node.getParent();
        if (parentId == TerraDBConstants.INVALID_PAGE_ID) return;

        Page parentPage = bufferPool.pin(parentId);
        InternalNode parent = new InternalNode(parentPage);
        int idx = parent.indexOfChild(node.getPageId());
        if (idx < 0) { bufferPool.unpin(parentPage, false); return; }

        // borrow from LEFT
        if (idx > 0) {
            int leftId = parent.getChild(idx - 1);
            Page leftPage = bufferPool.pin(leftId);
            InternalNode left = new InternalNode(leftPage);
            if (left.getCount() > InternalNode.MIN_KEYS) {
                long separator = parent.getKey(idx - 1);
                long upKey = left.getKey(left.getCount() - 1);
                int movingChild = left.getChild(left.getCount());
                node.prepend(separator, movingChild);
                Page mc = bufferPool.pin(movingChild);
                mc.putPayloadInt(0, node.getPageId());
                bufferPool.unpin(mc, true);
                parent.setKey(idx - 1, upKey);
                left.removeLast();
                bufferPool.unpin(leftPage, true);
                bufferPool.unpin(parentPage, true);
                return;
            }
            bufferPool.unpin(leftPage, false);
        }

        // borrow from RIGHT
        if (idx < parent.getCount()) {
            int rightId = parent.getChild(idx + 1);
            Page rightPage = bufferPool.pin(rightId);
            InternalNode right = new InternalNode(rightPage);
            if (right.getCount() > InternalNode.MIN_KEYS) {
                long separator = parent.getKey(idx);
                long upKey = right.getKey(0);
                int movingChild = right.getChild(0);
                node.append(separator, movingChild);
                Page mc = bufferPool.pin(movingChild);
                mc.putPayloadInt(0, node.getPageId());
                bufferPool.unpin(mc, true);
                parent.setKey(idx, upKey);
                right.removeFirst();
                bufferPool.unpin(rightPage, true);
                bufferPool.unpin(parentPage, true);
                return;
            }
            bufferPool.unpin(rightPage, false);
        }

        // merge
        if (idx > 0) {
            int leftId = parent.getChild(idx - 1);
            Page leftPage = bufferPool.pin(leftId);
            InternalNode left = new InternalNode(leftPage);
            long separator = parent.getKey(idx - 1);
            left.append(separator, node.getChild(0));
            for (int i = 0; i < node.getCount(); i++) {
                left.append(node.getKey(i), node.getChild(i + 1));
            }
            for (int c = 0; c <= node.getCount(); c++) {
                int childId = node.getChild(c);
                Page cp = bufferPool.pin(childId);
                cp.putPayloadInt(0, leftId);
                bufferPool.unpin(cp, true);
            }
            parent.removeAt(idx - 1);
            bufferPool.unpin(leftPage, true);
            bufferPool.unpin(nodePage, false);
            freePage(node.getPageId());
        } else {
            int rightId = parent.getChild(idx + 1);
            Page rightPage = bufferPool.pin(rightId);
            InternalNode right = new InternalNode(rightPage);
            long separator = parent.getKey(idx);
            node.append(separator, right.getChild(0));
            for (int i = 0; i < right.getCount(); i++) {
                node.append(right.getKey(i), right.getChild(i + 1));
            }
            for (int c = 0; c <= right.getCount(); c++) {
                int childId = right.getChild(c);
                Page cp = bufferPool.pin(childId);
                cp.putPayloadInt(0, node.getPageId());
                bufferPool.unpin(cp, true);
            }
            parent.removeAt(idx);
            bufferPool.unpin(rightPage, false);
            freePage(rightId);
        }

        finishParentFix(parent, parentPage);
    }

    private void finishParentFix(InternalNode parent, Page parentPage) {
        if (parentPage.getPageId() == rootPageId) {
            if (parent.getCount() == 0) {
                shrinkRootToChild(parent, parentPage);
            } else {
                bufferPool.unpin(parentPage, true);
            }
            return;
        }
        if (parent.getCount() < InternalNode.MIN_KEYS) {
            fixInternalUnderflow(parent, parentPage);
        } else {
            bufferPool.unpin(parentPage, true);
        }
    }

    private void shrinkRootToChild(InternalNode parent, Page parentPage) {
        int onlyChild = parent.getChild(0);
        Page childPage = bufferPool.pin(onlyChild);
        childPage.putPayloadInt(0, TerraDBConstants.INVALID_PAGE_ID);
        bufferPool.unpin(childPage, true);
        rootPageId = onlyChild;
        disk.setRootPageId(rootPageId);
        bufferPool.unpin(parentPage, false);
        freePage(parentPage.getPageId());
    }

    // ---------------- RANGE SCAN ----------------
    public List<KV> scan(long startKey, long endKey) {
        List<KV> out = new ArrayList<>();
        if (rootPageId == TerraDBConstants.INVALID_PAGE_ID) return out;

        Page curr = pinLeafForKey(startKey);
        boolean done = false;
        while (!done) {
            LeafNode leaf = new LeafNode(curr);
            for (int i = 0; i < leaf.getCount(); i++) {
                long k = leaf.getKey(i);
                if (k > endKey) { done = true; break; }
                if (k >= startKey) out.add(new KV(k, leaf.getVal(i)));
            }
            int next = leaf.getNext();
            bufferPool.unpin(curr, false);
            if (done || next == TerraDBConstants.INVALID_PAGE_ID) break;
            curr = bufferPool.pin(next);
        }
        return out;
    }

    // ---------------- helpers ----------------
    private Page pinLeafForKey(long key) {
        Page curr = bufferPool.pin(rootPageId);
        while (curr.getPageType() == PageType.BTREE_INTERNAL) {
            InternalNode in = new InternalNode(curr);
            int childId = in.getChild(in.findChildIndex(key));
            bufferPool.unpin(curr, false);
            curr = bufferPool.pin(childId);
        }
        return curr;
    }

    private void freePage(int pageId) {
        bufferPool.invalidate(pageId);
        disk.deallocatePage(pageId);
    }
}