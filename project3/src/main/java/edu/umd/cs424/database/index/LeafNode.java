package edu.umd.cs424.database.index;

import java.nio.ByteBuffer;
import java.util.*;

import edu.umd.cs424.database.BaseTransaction;
import edu.umd.cs424.database.common.Buffer;
import edu.umd.cs424.database.common.Pair;
import edu.umd.cs424.database.databox.DataBox;
import edu.umd.cs424.database.databox.Type;
import edu.umd.cs424.database.io.Page;
import edu.umd.cs424.database.table.RecordId;

/**
 * A leaf of a B+ tree. Every leaf in a B+ tree of order d stores between d and
 * 2d (key, record id) pairs and a pointer to its right sibling (i.e. the page
 * number of its right sibling). Moreover, every leaf node is serialized and
 * persisted on a single page; see toBytes and fromBytes for details on how a
 * leaf is serialized. For example, here is an illustration of two order 2
 * leafs connected together:
 *
 *   leaf 1 (stored on some page)          leaf 2 (stored on some other page)
 *   +-------+-------+-------+-------+     +-------+-------+-------+-------+
 *   | k0:r0 | k1:r1 | k2:r2 |       | --> | k3:r3 | k4:r4 |       |       |
 *   +-------+-------+-------+-------+     +-------+-------+-------+-------+
 */
class LeafNode extends BPlusNode {
    // Metadatta about the B+ tree hat this node belongs to.
    private final BPlusTreeMetadata metadata;

    // The page on which this leaf is serialized.
    private final Page page;

    // The keys and record ids of this leaf. `keys` is always sorted in ascending
    // order. The record id at index i corresponds to the key at index i. For
    // example, the keys [a, b, c] and the rids [1, 2, 3] represent the pairing
    // [a:1, b:2, c:3].
    //
    // Note the following subtlety. keys and rids are in-memory caches of the
    // keys and record ids stored on disk. Thus, consider what happens when you
    // create two LeafNode objects that point to the same page:
    //
    //   BPlusTreeMetadata meta = ...;
    //   int pageNum = ...;
    //   Page page = allocator.fetchPage(pageNum);
    //   ByteBuffer buf = page.getByteBuffer();
    //
    //   LeafNode leaf0 = LeafNode.fromBytes(buf, meta, pageNum);
    //   LeafNode leaf1 = LeafNode.fromBytes(buf, meta, pageNum);
    //
    // This scenario looks like this:
    //
    //   HEAP                        | DISK
    //   ===============================================================
    //   leaf0                       | page 42
    //   +-------------------------+ | +-------+-------+-------+-------+
    //   | keys = [k0, k1, k2]     | | | k0:r0 | k1:r1 | k2:r2 |       |
    //   | rids = [r0, r1, r2]     | | +-------+-------+-------+-------+
    //   | pageNum = 42            | |
    //   +-------------------------+ |
    //                               |
    //   leaf1                       |
    //   +-------------------------+ |
    //   | keys = [k0, k1, k2]     | |
    //   | rids = [r0, r1, r2]     | |
    //   | pageNum = 42            | |
    //   +-------------------------+ |
    //                               |
    //
    // Now imagine we perform on operation on leaf0 like leaf0.put(k3, r3). The
    // in-memory values of leaf0 will be updated and they will be synced to disk.
    // But, the in-memory values of leaf1 will not be updated. That will look
    // like this:
    //
    //   HEAP                        | DISK
    //   ===============================================================
    //   leaf0                       | page 42
    //   +-------------------------+ | +-------+-------+-------+-------+
    //   | keys = [k0, k1, k2, k3] | | | k0:r0 | k1:r1 | k2:r2 | k3:r3 |
    //   | rids = [r0, r1, r2, r3] | | +-------+-------+-------+-------+
    //   | pageNum = 42            | |
    //   +-------------------------+ |
    //                               |
    //   leaf1                       |
    //   +-------------------------+ |
    //   | keys = [k0, k1, k2]     | |
    //   | rids = [r0, r1, r2]     | |
    //   | pageNum = 42            | |
    //   +-------------------------+ |
    //                               |
    //
    // Make sure your code (or your tests) doesn't use stale in-memory cached
    // values of keys and rids.
    private List<DataBox> keys;
    private List<RecordId> rids;

    // If this leaf is the rightmost leaf, then rightSibling is Optional.empty().
    // Otherwise, rightSibling is Optional.of(n) where n is the page number of
    // this leaf's right sibling.
    private Optional<Integer> rightSibling;

    // Constructors //////////////////////////////////////////////////////////////
    /**
     * Construct a brand new leaf node. The leaf will be persisted on a brand new
     * page allocated by metadata.getAllocator().
     */
    public LeafNode(BPlusTreeMetadata metadata, List<DataBox> keys,
                    List<RecordId> rids, Optional<Integer> rightSibling, BaseTransaction transaction) {
        this(metadata, metadata.getAllocator().allocPage(transaction), keys, rids,
             rightSibling, transaction);
    }

    /**
     * Construct a leaf node that is persisted to page `pageNum` allocated by
     * metadata.getAllocator().
     */
    private LeafNode(BPlusTreeMetadata metadata, int pageNum, List<DataBox> keys,
                     List<RecordId> rids, Optional<Integer> rightSibling, BaseTransaction transaction) {
        assert(keys.size() == rids.size());

        this.metadata = metadata;
        this.page = metadata.getAllocator().fetchPage(transaction, pageNum);
        this.keys = keys;
        this.rids = rids;
        this.rightSibling = rightSibling;
        sync(transaction);
    }

    // Core API //////////////////////////////////////////////////////////////////
    // See BPlusNode.get.
    @Override
    public LeafNode get(BaseTransaction transaction, DataBox key) {
        //throw new UnsupportedOperationException("Implement this.");
        return this;
    }

    // See BPlusNode.getLeftmostLeaf.
    @Override
    public LeafNode getLeftmostLeaf(BaseTransaction transaction) {
        //throw new UnsupportedOperationException("Implement this.");
        return this;
    }

    // See BPlusNode.put.
    @Override
    public Optional<Pair<DataBox, Integer>> put(BaseTransaction transaction, DataBox key, RecordId rid)
    throws BPlusTreeException {
        //throw new UnsupportedOperationException("Implement this.");
        if(keys.contains(key)) {
        	throw new BPlusTreeException();
        }
        
        int index = InnerNode.numLessThanEqual(key, keys);
        keys.add(index, key);
        rids.add(index, rid);
        
        int d = metadata.getOrder();
        if(keys.size() <= (2*d)) {
        	sync(transaction);
        	return Optional.empty();
        }
        
        List<DataBox> leftKeys = keys.subList(0, d);
    	List<RecordId> leftChildren = rids.subList(0, d + 1);
    	List<DataBox> rightKeys = keys.subList(d, 2*d + 1);
    	List<RecordId> rightChildren = rids.subList(d, 2*d + 1);
        
        LeafNode n = new LeafNode(metadata, rightKeys, rightChildren, rightSibling, transaction);
        this.keys = leftKeys;
        this.rids = leftChildren;
        
        sync(transaction);
        return Optional.of(new Pair<>(rightKeys.get(0), n.getPage().getPageNum()));
    }

    // See BPlusNode.bulkLoad.
    @Override
    public Optional<Pair<DataBox, Integer>> bulkLoad(BaseTransaction transaction,
            Iterator<Pair<DataBox, RecordId>> data,
            float fillFactor)
    throws BPlusTreeException {
        //throw new UnsupportedOperationException("Implement this.");
        int d = metadata.getOrder();
		if(fillFactor * 2 * d <= 0) {
			throw new BPlusTreeException();
		}
		
		int numKeys = (int) Math.ceil(fillFactor * 2 * d);
		
		for(int i = keys.size(); i < numKeys && data.hasNext(); i++) {
			Pair<DataBox, RecordId> pair = data.next();
	        keys.add(pair.getFirst());
	        rids.add(pair.getSecond());
		}
		
		if(!data.hasNext()) {
			sync(transaction);
			return Optional.empty();
		}
    	
		List<DataBox> rightKeys = new ArrayList<>();
        List<RecordId> rightRids = new ArrayList<>();
        Pair<DataBox, RecordId> pair = data.next();
        rightKeys.add(0, pair.getFirst());
        rightRids.add(0, pair.getSecond());

        // Create right node.
        LeafNode n = new LeafNode(metadata, rightKeys, rightRids, Optional.empty(), transaction);
        int pageNum = n.getPage().getPageNum();

        // Update left node.
        this.rightSibling = Optional.of(pageNum);
        sync(transaction);

        return Optional.of(new Pair<>(rightKeys.get(0), pageNum));
    }

    // See BPlusNode.remove.
    @Override
    public void remove(BaseTransaction transaction, DataBox key) {
        //throw new UnsupportedOperationException("Implement this.");
        rids.remove(keys.indexOf(key));
        keys.remove(key);
        sync(transaction);
    }

    // Iterators /////////////////////////////////////////////////////////////////
    /** Return the record id associated with `key`. */
    public Optional<RecordId> getKey(DataBox key) {
        int index = keys.indexOf(key);
        return index == -1 ? Optional.empty() : Optional.of(rids.get(index));
    }

    /**
     * Returns an iterator over all the keys present in this node
     */
    public Iterator<DataBox> scanKeys() {
        return keys.iterator();
    }

    /**
     * Returns an iterator over the record ids of this leaf in ascending order of
     * their corresponding keys.
     */
    public Iterator<RecordId> scanAll() {
        return rids.iterator();
    }

    // Helpers ///////////////////////////////////////////////////////////////////
    @Override
    public Page getPage() {
        return page;
    }

    /** Returns the right sibling of this leaf, if it has one. */
    public Optional<LeafNode> getRightSibling(BaseTransaction transaction) {
        return rightSibling.flatMap(pageNum -> Optional.of(LeafNode.fromBytes(transaction, metadata, pageNum)));
    }

    /**
     * Returns the largest number d such that the serialization of a LeafNode
     * with 2d entries will fit on a single page of size `pageSizeInBytes`.
     */
    public static int maxOrder(int pageSizeInBytes, Type keySchema) {
        // A leaf node with k entries takes up the following number of bytes:
        //
        //   1 + 4 + 4 + k * (keySize + ridSize)
        //
        // where
        //
        //   - 1 is the number of bytes used to store isLeaf,
        //   - 4 is the number of bytes used to store a sibling pointer,
        //   - 4 is the number of bytes used to store k,
        //   - keySize is the number of bytes used to store a DataBox of type
        //     keySchema, and
        //   - ridSize is the number of bytes of a RecordId.
        //
        // Solving the following equation
        //
        //   k * (keySize + ridSize) + 9 <= pageSizeInBytes
        //
        // we get
        //
        //   k = (pageSizeInBytes - 9) / (keySize + ridSize)
        //
        // The order d is half of k.
        int keySize = keySchema.getSizeInBytes();
        int ridSize = RecordId.getSizeInBytes();
        int k = (pageSizeInBytes - 9) / (keySize + ridSize);
        return k / 2;
    }

    // For testing only.
    List<DataBox> getKeys() {
        return keys;
    }

    // For testing only.
    List<RecordId> getRids() {
        return rids;
    }

    // Pretty Printing ///////////////////////////////////////////////////////////
    @Override
    public String toString() {
        return String.format("LeafNode(pageNum=%s, keys=%s, rids=%s)",
                             page.getPageNum(), keys, rids);
    }

    @Override
    public String toSexp(BaseTransaction transaction) {
        var ss = new ArrayList<String>();
        for (int i = 0; i < keys.size(); ++i) {
            String key = keys.get(i).toString();
            String rid = rids.get(i).toSexp();
            ss.add(String.format("(%s %s)", key, rid));
        }
        return String.format("(%s)", String.join(" ", ss));
    }

    /**
     * Given a leaf with page number 1 and three (key, rid) pairs (0, (0, 0)),
     * (1, (1, 1)), and (2, (2, 2)), the corresponding dot fragment is:
     * <p>
     *   node1[label = "{0: (0 0)|1: (1 1)|2: (2 2)}"];
     */
    @Override
    public String toDot(BaseTransaction transaction) {
        var ss = new ArrayList<String>();
        for (int i = 0; i < keys.size(); ++i) {
            ss.add(String.format("%s: %s", keys.get(i), rids.get(i).toSexp()));
        }
        int pageNum = getPage().getPageNum();
        String s = String.join("|", ss);
        return String.format("  node%d[label = \"{%s}\"];", pageNum, s);
    }

    // Serialization /////////////////////////////////////////////////////////////
    @Override
    public byte[] toBytes() {
        // When we serialize a leaf node, we write:
        //
        //   a. the literal value 1 (1 byte) which indicates that this node is a
        //      leaf node,
        //   b. the value 1 or 0 (1 byte) which indicates whether this node has a right sibling
        //   c. the page id (4 bytes) of our right sibling, this field is ignored if the
        //      right sibling indicator is 0.
        //   d. the number (4 bytes) of keys (K) this leaf node contains (key, rid) pairs this leaf node contains.
        //   e. the K keys of this node
        //   f. the K rids of this node
        //
        // For example, the following bytes:
        //
        //   +----+----+-------------+-------------+----+----+-------------------+-------------------+
        //   | 01 | 01 | 00 00 00 04 | 00 00 00 02 | 05 | 07 | 00 00 00 03 00 01 | 00 00 00 04 00 06 |
        //   +----+----+-------------+-------------+----+----+-------------------+-------------------+
        //    \__/ \__/ \___________/ \___________/ \_______/ \_____________________________________/
        //     a     b         c            d           e                        f
        //
        // represent a leaf node having a sibling on page 4, two keys [5, 7] and two corresponding records [(3, 1), (4, 6)]

        // All sizes are in bytes.
        int isLeafSize = 1;
        int hasSiblingSize = 1;
        int siblingSize = Integer.BYTES;
        int lenSize = Integer.BYTES;
        int keySize = metadata.getKeySchema().getSizeInBytes() * keys.size();
        int ridSize = RecordId.getSizeInBytes() * keys.size();
        int size = isLeafSize + hasSiblingSize + siblingSize + lenSize + keySize + ridSize;

        ByteBuffer buf = ByteBuffer.allocate(size);
        buf.put((byte) 1);
        buf.put((byte) (rightSibling.isPresent() ? 1 : 0));
        buf.putInt(rightSibling.orElse(0));
        buf.putInt(keys.size());
        for (DataBox key : keys) {
            buf.put(key.toBytes());
        }
        for (int i = 0; i < keys.size(); ++i) {
            buf.put(rids.get(i).toBytes());
        }
        return buf.array();
    }

    /**
     * LeafNode.fromBytes(m, p) loads a LeafNode from page p of
     * meta.getAllocator().
     */
    public static LeafNode fromBytes(BaseTransaction transaction, BPlusTreeMetadata metadata,
                                     int pageNum) {

        //throw new UnsupportedOperationException("Implement this.");

        Page page = metadata.getAllocator().fetchPage(transaction, pageNum);
    	Buffer buf = page.getBuffer(transaction);

        System.out.println(page);

    	assert(buf.get() == (byte) 1);
        List<DataBox> newKeys = new ArrayList<>();
        List<RecordId> newRids = new ArrayList<>();
        
        Optional<Integer> rightSibling = buf.get() == -1 ? Optional.empty() : Optional.of(buf.getInt());
        
        int k = buf.getInt();
        for (int i = 0; i < k; ++i) {
            newKeys.add(DataBox.fromBytes(buf, metadata.getKeySchema()));
        }
        for (int i = 0; i < k; ++i) {
        	newRids.add(RecordId.fromBytes(buf));
        }
        
        return new LeafNode(metadata, pageNum, newKeys, newRids, rightSibling, transaction);
    }

    // Builtins //////////////////////////////////////////////////////////////////
    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof LeafNode node)) {
            return false;
        }
        return page.getPageNum() == node.page.getPageNum() &&
               keys.equals(node.keys) &&
               rids.equals(node.rids) &&
               rightSibling.equals(node.rightSibling);
    }

    @Override
    public int hashCode() {
        return Objects.hash(page.getPageNum(), keys, rids, rightSibling);
    }
}
