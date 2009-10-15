/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.spellchecker.trie;


import java.io.Serializable;
import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedMap;

/**
 * A PATRICIA Trie.
 * <p/>
 * PATRICIA = Practical Algorithm to Retrieve Information Coded in Alphanumeric
 * <p/>
 * A PATRICIA Trie is a compressed Trie. Instead of storing all data at the
 * edges of the Trie (and having empty internal nodes), PATRICIA stores data
 * in every node. This allows for very efficient traversal, insert, delete,
 * predecessor, successor, prefix, range, and 'select' operations. All operations
 * are performed at worst in O(K) time, where K is the number of bits in the
 * largest item in the tree. In practice, operations actually take O(A(K))
 * time, where A(K) is the average number of bits of all items in the tree.
 * <p/>
 * Most importantly, PATRICIA requires very few comparisons to keys while
 * doing any operation. While performing a lookup, each comparison
 * (at most K of them, described above) will perform a single bit comparison
 * against the given key, instead of comparing the entire key to another key.
 * <p/>
 * The Trie can return operations in lexicographical order using the 'traverse',
 * 'prefix', 'submap', or 'iterator' methods. The Trie can also scan for items
 * that are 'bitwise' (using an XOR metric) by the 'select' method. Bitwise
 * closeness is determined by the {@link KeyAnalyzer} returning true or
 * false for a bit being set or not in a given key.
 * <p/>
 * This PATRICIA Trie supports both variable length & fixed length keys.
 * Some methods, such as <code>getPrefixedBy(...)</code> are suited only to
 * variable length keys, whereas <code>getPrefixedByBits(...)</code> is suited
 * to fixed-size keys.
 * <p/>
 * Additionally see <a
 * href="http://www.csse.monash.edu.au/~lloyd/tildeAlgDS/Tree/PATRICIA/">PATRICIA</a>
 * for more information.
 * <p/>
 * Any methods here that take an <code>Object</code> may throw a
 * <code>ClassCastException<code> if the method is expecting an instance of K
 * (and it isn't K).
 * <p/>
 * <pre>
 * PatriciaTrie&lt;String, String&gt; trie = new PatriciaTrie&lt;String, String&gt;
 * (new CharSequenceKeyAnalyzer());
 * <p/>
 * trie.put("Lime", "Lime");
 * trie.put("LimeWire", "LimeWire");
 * trie.put("LimeRadio", "LimeRadio");
 * trie.put("Lax", "Lax");
 * trie.put("Lake", "Lake");
 * trie.put("Lovely", "Lovely");
 * <p/>
 * System.out.println(trie.select("Lo"));
 * System.out.println(trie.select("Lime"));
 * <p/>
 * System.out.println(trie.getPrefixedBy("La").toString());
 * <p/>
 * Output:
 * Lovely
 * Lime
 * {Lake=Lake, Lax=Lax}
 * <p/>
 * </pre>
 *
 * @author Roger Kapsi
 * @author Sam Berlin
 */
public class PatriciaTrie<K, V> extends AbstractMap<K, V> implements Trie<K, V>, Serializable {

  private static final long serialVersionUID = 110232526181493307L;

  /**
   * The root element of the Trie.
   */
  private final TrieEntry<K, V> root = new TrieEntry<K, V>(null, null, -1);

  /**
   * The current size (total number of elements) of the Trie.
   */
  private int size = 0;

  /**
   * The number of times this has been modified (to fail-fast the iterators).
   */
  private transient int modCount = 0;

  /**
   * The keyAnalyzer used to analyze bit values of keys.
   */
  private final KeyAnalyzer<? super K> keyAnalyzer;

  /**
   * Constructs a new PatriciaTrie using the given keyAnalyzer.
   */
  public PatriciaTrie(KeyAnalyzer<? super K> keyAnalyzer) {
    this.keyAnalyzer = keyAnalyzer;
  }

  /**
   * Returns the KeyAnalyzer that constructed the trie.
   */
  public KeyAnalyzer<? super K> getKeyAnalyzer() {
    return keyAnalyzer;
  }

  /**
   * Returns the KeyAnalyzer as a comparator.
   */
  public Comparator<? super K> comparator() {
    return keyAnalyzer;
  }

  /**
   * Clears the Trie (i.e. removes all elements).
   */
  public void clear() {
    root.key = null;
    root.bitIndex = -1;
    root.value = null;

    root.parent = null;
    root.left = root;
    root.right = null;
    root.predecessor = root;

    size = 0;
    incrementModCount();
  }

  /**
   * Returns true if the Trie is empty
   */
  public boolean isEmpty() {
    return size == 0;
  }

  /**
   * Returns the number items in the Trie
   */
  public int size() {
    return size;
  }

  /**
   * Increments both the size and mod counter.
   */
  private void incrementSize() {
    size++;
    incrementModCount();
  }

  /**
   * Decrements the size and increments the mod counter.
   */
  private void decrementSize() {
    size--;
    incrementModCount();
  }

  /**
   * Increments the mod counter.
   */
  private void incrementModCount() {
    modCount++;
  }

  /**
   * Adds a new <key, value> pair to the Trie and if a pair already
   * exists it will be replaced. In the latter case it will return
   * the old value.
   */
  public V put(K key, V value) {
    if (key == null) {
      throw new NullPointerException("Key cannot be null");
    }

    int keyLength = length(key);

    // The only place to store a key with a length
    // of zero bits is the root node
    if (keyLength == 0) {
      if (root.isEmpty()) {
        incrementSize();
      }
      else {
        incrementModCount();
      }
      return root.setKeyValue(key, value);
    }

    TrieEntry<K, V> found = getNearestEntryForKey(key, keyLength);
    if (key.equals(found.key)) {
      if (found.isEmpty()) { // <- must be the root
        incrementSize();
      }
      else {
        incrementModCount();
      }
      return found.setKeyValue(key, value);
    }

    int bitIndex = bitIndex(key, found.key);
    if (isValidBitIndex(bitIndex)) { // in 99.999...9% the case
      /* NEW KEY+VALUE TUPLE */
      TrieEntry<K, V> t = new TrieEntry<K, V>(key, value, bitIndex);
      addEntry(t, keyLength);
      incrementSize();
      return null;
    }
    else if (isNullBitKey(bitIndex)) {
      // A bits of the Key are zero. The only place to
      // store such a Key is the root Node!

      /* NULL BIT KEY */
      if (root.isEmpty()) {
        incrementSize();
      }
      else {
        incrementModCount();
      }
      return root.setKeyValue(key, value);

    }
    else if (isEqualBitKey(bitIndex)) {
      // This is a very special and rare case.

      /* REPLACE OLD KEY+VALUE */
      if (found != root) {
        incrementModCount();
        return found.setKeyValue(key, value);
      }
    }

    throw new IndexOutOfBoundsException("Failed to put: " + key + " -> " + value + ", " + bitIndex);
  }

  /**
   * Adds the given entry into the Trie.
   */
  private TrieEntry<K, V> addEntry(TrieEntry<K, V> toAdd, int keyLength) {
    TrieEntry<K, V> current = root.left;
    TrieEntry<K, V> path = root;
    while (true) {
      if (current.bitIndex >= toAdd.bitIndex || current.bitIndex <= path.bitIndex) {
        toAdd.predecessor = toAdd;

        if (!isBitSet(toAdd.key, keyLength, toAdd.bitIndex)) {
          toAdd.left = toAdd;
          toAdd.right = current;
        }
        else {
          toAdd.left = current;
          toAdd.right = toAdd;
        }

        toAdd.parent = path;
        if (current.bitIndex >= toAdd.bitIndex) {
          current.parent = toAdd;
        }

        // if we inserted an uplink, set the predecessor on it
        if (current.bitIndex <= path.bitIndex) {
          current.predecessor = toAdd;
        }

        if (path == root || !isBitSet(toAdd.key, keyLength, path.bitIndex)) {
          path.left = toAdd;
        }
        else {
          path.right = toAdd;
        }
        return toAdd;
      }

      path = current;
      if (!isBitSet(toAdd.key, keyLength, current.bitIndex)) {
        current = current.left;
      }
      else {
        current = current.right;
      }
    }
  }

  @Override
  public Set<Map.Entry<K, V>> entrySet() {
    Set<Map.Entry<K, V>> es = entrySet;
    return (es != null ? es : (entrySet = new EntrySet()));
  }

  /**
   * Returns the Value whose Key equals our lookup Key
   * or null if no such key exists.
   */
  public V get(Object k) {
    TrieEntry<K, V> entry = getEntry(k);
    return entry != null ? entry.getValue() : null;
  }

  /**
   * Returns the entry associated with the specified key in the
   * PatriciaTrie.  Returns null if the map contains no mapping
   * for this key.
   * <p/>
   * This may throw ClassCastException if the object is not of type K.
   */
  TrieEntry<K, V> getEntry(Object k) {
    K key = asKey(k);
    if (key == null) return null;

    int keyLength = length(key);
    TrieEntry<K, V> entry = getNearestEntryForKey(key, keyLength);
    return !entry.isEmpty() && key.equals(entry.key) ? entry : null;
  }

  /**
   * Gets the key as a 'K'.
   */
  @SuppressWarnings("unchecked")
  protected final K asKey(Object key) {
    try {
      return (K)key;
    }
    catch (ClassCastException cce) {
      // Because the type is erased, the cast & return are
      // actually doing nothing, making this CCE impossible.
      // However, it's still here on the off-chance it may
      // work.
      return null;
    }
  }

  /**
   * Returns the nearest entry for a given key.  This is useful
   * for finding knowing if a given key exists (and finding the value
   * for it), or for inserting the key.
   * <p/>
   * The actual get implementation. This is very similar to
   * selectR but with the exception that it might return the
   * root Entry even if it's empty.
   */
  private TrieEntry<K, V> getNearestEntryForKey(K key, int keyLength) {
    TrieEntry<K, V> current = root.left;
    TrieEntry<K, V> path = root;
    while (true) {
      if (current.bitIndex <= path.bitIndex) return current;

      path = current;
      if (!isBitSet(key, keyLength, current.bitIndex)) {
        current = current.left;
      }
      else {
        current = current.right;
      }
    }
  }

  /**
   * Returns the Value whose Key has the longest prefix
   * in common with our lookup key.
   */
  @SuppressWarnings("unchecked")
  public V select(K key) {
    int keyLength = length(key);
    TrieEntry[] result = new TrieEntry[1];
    if (!selectR(root.left, -1, key, keyLength, result)) {
      TrieEntry<K, V> e = result[0];
      return e.getValue();
    }
    return null;
  }

  /**
   * This is equivalent to the other selectR() method but without
   * its overhead because we're selecting only one best matching
   * Entry from the Trie.
   */
  private boolean selectR(TrieEntry<K, V> h, int bitIndex, final K key, final int keyLength, final TrieEntry[] result) {

    if (h.bitIndex <= bitIndex) {
      // If we hit the root Node and it is empty
      // we have to look for an alternative best
      // matching node.
      if (!h.isEmpty()) {
        result[0] = h;
        return false;
      }
      return true;
    }

    if (!isBitSet(key, keyLength, h.bitIndex)) {
      if (selectR(h.left, h.bitIndex, key, keyLength, result)) {
        return selectR(h.right, h.bitIndex, key, keyLength, result);
      }
    }
    else {
      if (selectR(h.right, h.bitIndex, key, keyLength, result)) {
        return selectR(h.left, h.bitIndex, key, keyLength, result);
      }
    }
    return false;
  }

  @SuppressWarnings("unchecked")
  public Map.Entry<K, V> select(K key, Cursor<? super K, ? super V> cursor) {
    int keyLength = length(key);
    TrieEntry[] result = new TrieEntry[]{null};
    selectR(root.left, -1, key, keyLength, cursor, result);
    return result[0];
  }

  private boolean selectR(TrieEntry<K, V> h,
                          int bitIndex,
                          final K key,
                          final int keyLength,
                          final Cursor<? super K, ? super V> cursor,
                          final TrieEntry[] result) {

    if (h.bitIndex <= bitIndex) {
      if (!h.isEmpty()) {
        Cursor.SelectStatus ret = cursor.select(h);
        switch (ret) {
          case REMOVE:
            throw new UnsupportedOperationException("cannot remove during select");
          case EXIT:
            result[0] = h;
            return false; // exit
          case REMOVE_AND_EXIT:
            TrieEntry<K, V> entry = new TrieEntry<K, V>(h.getKey(), h.getValue(), -1);
            result[0] = entry;
            removeEntry(h);
            return false;
          case CONTINUE:
            // fall through.
        }
      }
      return true; // continue
    }

    if (!isBitSet(key, keyLength, h.bitIndex)) {
      if (selectR(h.left, h.bitIndex, key, keyLength, cursor, result)) {
        return selectR(h.right, h.bitIndex, key, keyLength, cursor, result);
      }
    }
    else {
      if (selectR(h.right, h.bitIndex, key, keyLength, cursor, result)) {
        return selectR(h.left, h.bitIndex, key, keyLength, cursor, result);
      }
    }

    return false;
  }

  /**
   * Returns a view of this Trie of all elements that are
   * prefixed by the given key.
   * <p/>
   * In a fixed-keysize Trie, this is essentially a 'get' operation.
   * <p/>
   * For example, if the trie contains 'Lime', 'LimeWire',
   * 'LimeRadio', 'Lax', 'Later', 'Lake', and 'Lovely', then
   * a lookup of 'Lime' would return 'Lime', 'LimeRadio', and 'LimeWire'.
   * <p/>
   * The view that this returns is optimized to have a very efficient
   * Iterator. The firstKey, lastKey & size methods must iterate
   * over all possible values in order to determine the results. This
   * information is cached until the Patricia tree changes. All other
   * methods (except Iterator) must compare the given key to the prefix
   * to ensure that it is within the range of the view. The Iterator's
   * remove method must also relocate the subtree that contains the
   * prefixes if the entry holding the subtree is removed or changes.
   * Changing the subtree takes O(K) time.
   *
   * @param key
   */
  public SortedMap<K, V> getPrefixedBy(K key) {
    return getPrefixedByBits(key, 0, keyAnalyzer.length(key));
  }

  /**
   * Returns a view of this Trie of all elements that are
   * prefixed by the length of the key.
   * <p/>
   * Fixed-keysize Tries will not support this operation
   * (because all keys will be the same length).
   * <p/>
   * For example, if the trie contains 'Lime', 'LimeWire',
   * 'LimeRadio', 'Lax', 'Later', 'Lake', and 'Lovely', then
   * a lookup of 'LimePlastics' with a length of 4 would
   * return 'Lime', 'LimeRadio', and 'LimeWire'.
   * <p/>
   * The view that this returns is optimized to have a very efficient
   * Iterator.  The firstKey, lastKey & size methods must iterate
   * over all possible values in order to determine the results.  This
   * information is cached until the Patricia tree changes.  All other
   * methods (except Iterator) must compare the given key to the prefix
   * to ensure that it is within the range of the view.  The Iterator's
   * remove method must also relocate the subtree that contains the
   * prefixes if the entry holding the subtree is removed or changes.
   * Changing the subtree takes O(K) time.
   *
   * @param key
   * @param length
   */
  public SortedMap<K, V> getPrefixedBy(K key, int length) {
    return getPrefixedByBits(key, 0, length * keyAnalyzer.bitsPerElement());
  }

  /**
   * Returns a view of this Trie of all elements that are prefixed
   * by the key, starting at the given offset and for the given length.
   * <p/>
   * Fixed-keysize Tries will not support this operation
   * (because all keys are the same length).
   * <p/>
   * For example, if the trie contains 'Lime', 'LimeWire',
   * 'LimeRadio', 'Lax', 'Later', 'Lake', and 'Lovely', then
   * a lookup of 'The Lime Plastics' with an offset of 4 and a
   * length of 4 would return 'Lime', 'LimeRadio', and 'LimeWire'.
   * <p/>
   * The view that this returns is optimized to have a very efficient
   * Iterator.  The firstKey, lastKey & size methods must iterate
   * over all possible values in order to determine the results.  This
   * information is cached until the Patricia tree changes.  All other
   * methods (except Iterator) must compare the given key to the prefix
   * to ensure that it is within the range of the view.  The Iterator's
   * remove method must also relocate the subtree that contains the
   * prefixes if the entry holding the subtree is removed or changes.
   * Changing the subtree takes O(K) time.
   *
   * @param key
   * @param offset
   * @param length
   */
  public SortedMap<K, V> getPrefixedBy(K key, int offset, int length) {
    return getPrefixedByBits(key, offset * keyAnalyzer.bitsPerElement(), length * keyAnalyzer.bitsPerElement());
  }

  /**
   * Returns a view of this Trie of all elements that are prefixed
   * by the number of bits in the given Key.
   * <p/>
   * Fixed-keysize Tries can support this operation as a way to do
   * lookups of partial keys.  That is, if the Trie is storing IP
   * addresses, you can lookup all addresses that begin with
   * '192.168' by providing the key '192.168.X.X' and a length of 16
   * would return all addresses that begin with '192.168'.
   * <p/>
   * The view that this returns is optimized to have a very efficient
   * Iterator.  The firstKey, lastKey & size methods must iterate
   * over all possible values in order to determine the results.  This
   * information is cached until the Patricia tree changes.  All other
   * methods (except Iterator) must compare the given key to the prefix
   * to ensure that it is within the range of the view.  The Iterator's
   * remove method must also relocate the subtree that contains the
   * prefixes if the entry holding the subtree is removed or changes.
   * Changing the subtree takes O(K) time.
   *
   * @param key
   * @param bitLength
   */
  public SortedMap<K, V> getPrefixedByBits(K key, int bitLength) {
    return getPrefixedByBits(key, 0, bitLength);
  }

  /**
   * Returns a view of this map, with entries containing only those that
   * are prefixed by a value whose bits matches the bits between 'offset'
   * and 'length' in the given key.
   * <p/>
   * The view that this returns is optimized to have a very efficient
   * Iterator.  The firstKey, lastKey & size methods must iterate
   * over all possible values in order to determine the results.  This
   * information is cached until the Patricia tree changes.  All other
   * methods (except Iterator) must compare the given key to the prefix
   * to ensure that it is within the range of the view.  The Iterator's
   * remove method must also relocate the subtree that contains the
   * prefixes if the entry holding the subtree is removed or changes.
   * Changing the subtree takes O(K) time.
   *
   * @param key
   * @param offset
   * @param length
   */
  private SortedMap<K, V> getPrefixedByBits(K key, int offset, int length) {
    int offsetLength = offset + length;
    if (offsetLength > length(key)) {
      throw new IllegalArgumentException(offset + " + " + length + " > " + length(key));
    }

    if (offsetLength == 0) return this;

    return new PrefixSubMap(key, offset, length);
  }

  /**
   * Returns true if this trie contains the specified Key
   * <p/>
   * This may throw ClassCastException if the object is not
   * of type K.
   */
  public boolean containsKey(Object k) {
    K key = asKey(k);
    if (key == null) return false;

    int keyLength = length(key);
    TrieEntry entry = getNearestEntryForKey(key, keyLength);
    return !entry.isEmpty() && key.equals(entry.key);
  }

  /**
   * Returns true if this Trie contains the specified value.
   */
  public boolean containsValue(Object o) {
    for (V v : values()) {
      if (valEquals(v, o)) return true;
    }
    return false;
  }


  /**
   * Removes a Key from the Trie if one exists
   * <p/>
   * This may throw ClassCastException if the object is not of type K.
   *
   * @param k the Key to delete
   * @return Returns the deleted Value
   */
  public V remove(Object k) {
    K key = asKey(k);
    if (key == null) return null;

    int keyLength = length(key);
    TrieEntry<K, V> current = root.left;
    TrieEntry<K, V> path = root;
    while (true) {
      if (current.bitIndex <= path.bitIndex) {
        if (!current.isEmpty() && key.equals(current.key)) {
          return removeEntry(current);
        }
        else {
          return null;
        }
      }

      path = current;
      if (!isBitSet(key, keyLength, current.bitIndex)) {
        current = current.left;
      }
      else {
        current = current.right;
      }
    }
  }

  /**
   * Removes a single entry from the Trie.
   * <p/>
   * If we found a Key (Entry h) then figure out if it's
   * an internal (hard to remove) or external Entry (easy
   * to remove)
   */
  private V removeEntry(TrieEntry<K, V> h) {
    if (h != root) {
      if (h.isInternalNode()) {
        removeInternalEntry(h);
      }
      else {
        removeExternalEntry(h);
      }
    }

    decrementSize();
    return h.setKeyValue(null, null);
  }

  /**
   * Removes an external entry from the Trie.
   * <p/>
   * If it's an external Entry then just remove it.
   * This is very easy and straight forward.
   */
  private void removeExternalEntry(TrieEntry<K, V> h) {
    if (h == root) {
      throw new IllegalArgumentException("Cannot delete root Entry!");
    }
    else if (!h.isExternalNode()) {
      throw new IllegalArgumentException(h + " is not an external Entry!");
    }

    TrieEntry<K, V> parent = h.parent;
    TrieEntry<K, V> child = (h.left == h) ? h.right : h.left;

    if (parent.left == h) {
      parent.left = child;
    }
    else {
      parent.right = child;
    }

    // either the parent is changing, or the predecessor is changing.
    if (child.bitIndex > parent.bitIndex) {
      child.parent = parent;
    }
    else {
      child.predecessor = parent;
    }

  }

  /**
   * Removes an internal entry from the Trie.
   * <p/>
   * If it's an internal Entry then "good luck" with understanding
   * this code. The Idea is essentially that Entry p takes Entry h's
   * place in the trie which requires some re-wiring.
   */
  private void removeInternalEntry(TrieEntry<K, V> h) {
    if (h == root) {
      throw new IllegalArgumentException("Cannot delete root Entry!");
    }
    else if (!h.isInternalNode()) {
      throw new IllegalArgumentException(h + " is not an internal Entry!");
    }

    TrieEntry<K, V> p = h.predecessor;

    // Set P's bitIndex
    p.bitIndex = h.bitIndex;

    // Fix P's parent, predecessor and child Nodes
    {
      TrieEntry<K, V> parent = p.parent;
      TrieEntry<K, V> child = (p.left == h) ? p.right : p.left;

      // if it was looping to itself previously,
      // it will now be pointed from it's parent
      // (if we aren't removing it's parent --
      //  in that case, it remains looping to itself).
      // otherwise, it will continue to have the same
      // predecessor.
      if (p.predecessor == p && p.parent != h) p.predecessor = p.parent;

      if (parent.left == p) {
        parent.left = child;
      }
      else {
        parent.right = child;
      }

      if (child.bitIndex > parent.bitIndex) {
        child.parent = parent;
      }
    }
    ;

    // Fix H's parent and child Nodes
    {
      // If H is a parent of its left and right child
      // then change them to P
      if (h.left.parent == h) {
        h.left.parent = p;
      }

      if (h.right.parent == h) {
        h.right.parent = p;
      }

      // Change H's parent
      if (h.parent.left == h) {
        h.parent.left = p;
      }
      else {
        h.parent.right = p;
      }
    }
    ;

    // Copy the remaining fields from H to P
    //p.bitIndex = h.bitIndex;
    p.parent = h.parent;
    p.left = h.left;
    p.right = h.right;

    // Make sure that if h was pointing to any uplinks,
    // p now points to them.
    if (isValidUplink(p.left, p)) p.left.predecessor = p;
    if (isValidUplink(p.right, p)) p.right.predecessor = p;

  }

  /**
   * Returns the node lexicographically before the given node (or null if none).
   * <p/>
   * This follows four simple branches:
   * - If the uplink that returned us was a right uplink:
   * - If predecessor's left is a valid uplink from predecessor, return it.
   * - Else, follow the right path from the predecessor's left.
   * - If the uplink that returned us was a left uplink:
   * - Loop back through parents until we encounter a node where
   * node != node.parent.left.
   * - If node.parent.left is uplink from node.parent:
   * - If node.parent.left is not root, return it.
   * - If it is root & root isEmpty, return null.
   * - If it is root & root !isEmpty, return root.
   * - If node.parent.left is not uplink from node.parent:
   * - Follow right path for first right child from node.parent.left
   *
   * @param start
   */
  private TrieEntry<K, V> previousEntry(TrieEntry<K, V> start) {
    if (start.predecessor == null) throw new IllegalArgumentException("must have come from somewhere!");

    if (start.predecessor.right == start) {
      if (isValidUplink(start.predecessor.left, start.predecessor)) {
        return start.predecessor.left;
      }
      else {
        return followRight(start.predecessor.left);
      }
    }
    else {
      TrieEntry<K, V> node = start.predecessor;
      while (node.parent != null && node == node.parent.left) node = node.parent;
      if (node.parent == null) // can be null if we're looking up root.
      {
        return null;
      }
      if (isValidUplink(node.parent.left, node.parent)) {
        if (node.parent.left == root) {
          if (root.isEmpty()) {
            return null;
          }
          else {
            return root;
          }
        }
        else {
          return node.parent.left;
        }
      }
      else {
        return followRight(node.parent.left);
      }
    }
  }

  /**
   * Returns the entry lexicographically after the given entry.
   * If the given entry is null, returns the first node.
   */
  private TrieEntry<K, V> nextEntry(TrieEntry<K, V> node) {
    if (node == null) {
      return firstEntry();
    }
    else {
      return nextEntryImpl(node.predecessor, node, null);
    }
  }

  /**
   * Returns the entry lexicographically after the given entry.
   * If the given entry is null, returns the first node.
   * <p/>
   * This will traverse only within the subtree.  If the given node
   * is not within the subtree, this will have undefined results.
   */
  private TrieEntry<K, V> nextEntryInSubtree(TrieEntry<K, V> node, TrieEntry<K, V> parentOfSubtree) {
    if (node == null) {
      return firstEntry();
    }
    else {
      return nextEntryImpl(node.predecessor, node, parentOfSubtree);
    }
  }

  /**
   * Scans for the next node, starting at the specified point, and using 'previous'
   * as a hint that the last node we returned was 'previous' (so we know not to return
   * it again).  If 'tree' is non-null, this will limit the search to the given tree.
   * <p/>
   * The basic premise is that each iteration can follow the following steps:
   * <p/>
   * 1) Scan all the way to the left.
   * a) If we already started from this node last time, proceed to Step 2.
   * b) If a valid uplink is found, use it.
   * c) If the result is an empty node (root not set), break the scan.
   * d) If we already returned the left node, break the scan.
   * <p/>
   * 2) Check the right.
   * a) If we already returned the right node, proceed to Step 3.
   * b) If it is a valid uplink, use it.
   * c) Do Step 1 from the right node.
   * <p/>
   * 3) Back up through the parents until we encounter find a parent
   * that we're not the right child of.
   * <p/>
   * 4) If there's no right child of that parent, the iteration is finished.
   * Otherwise continue to Step 5.
   * <p/>
   * 5) Check to see if the right child is a valid uplink.
   * a) If we already returned that child, proceed to Step 6.
   * Otherwise, use it.
   * <p/>
   * 6) If the right child of the parent is the parent itself, we've
   * already found & returned the end of the Trie, so exit.
   * <p/>
   * 7) Do Step 1 on the parent's right child.
   */
  private TrieEntry<K, V> nextEntryImpl(TrieEntry<K, V> start, TrieEntry<K, V> previous, TrieEntry<K, V> tree) {
    TrieEntry<K, V> current = start;

    // Only look at the left if this was a recursive or
    // the first check, otherwise we know we've already looked
    // at the left.
    if (previous == null || start != previous.predecessor) {
      while (!current.left.isEmpty()) {
        // stop traversing if we've already
        // returned the left of this node.
        if (previous == current.left) {
          break;
        }

        if (isValidUplink(current.left, current)) {
          return current.left;
        }

        current = current.left;
      }
    }

    // If there's no data at all, exit.
    if (current.isEmpty()) {
      return null;
    }

    // If we've already returned the left,
    // and the immediate right is null,
    // there's only one entry in the Trie
    // which is stored at the root.
    //
    //  / ("")   <-- root
    //  \_/  \
    //       null <-- 'current'
    //
    if (current.right == null) return null;

    // If nothing valid on the left, try the right.
    if (previous != current.right) {
      // See if it immediately is valid.
      if (isValidUplink(current.right, current)) {
        return current.right;
      }

      // Must search on the right's side if it wasn't initially valid.
      return nextEntryImpl(current.right, previous, tree);
    }

    // Neither left nor right are valid, find the first parent
    // whose child did not come from the right & traverse it.
    while (current == current.parent.right) {
      // If we're going to traverse to above the subtree, stop.
      if (current == tree) return null;

      current = current.parent;
    }

    // If we're on the top of the subtree, we can't go any higher.
    if (current == tree) return null;


    // If there's no right, the parent must be root, so we're done.
    if (current.parent.right == null) {
      return null;
    }


    // If the parent's right points to itself, we've found one.
    if (previous != current.parent.right && isValidUplink(current.parent.right, current.parent)) {
      return current.parent.right;
    }

    // If the parent's right is itself, there can't be any more nodes.
    if (current.parent.right == current.parent) {
      return null;
    }

    // We need to traverse down the parent's right's path.
    return nextEntryImpl(current.parent.right, previous, tree);
  }

  /**
   * Returns each entry as a string.
   */
  public String toString() {
    StringBuilder buffer = new StringBuilder();
    buffer.append("Trie[").append(size()).append("]={\n");
    for (Iterator<Map.Entry<K, V>> i = newEntryIterator(); i.hasNext();) {
      buffer.append("  ").append(i.next().toString()).append("\n");
    }
    buffer.append("}\n");
    return buffer.toString();
  }

  public Map.Entry<K, V> traverse(Cursor<? super K, ? super V> cursor) {
    TrieEntry<K, V> entry = nextEntry(null);
    while (entry != null) {
      TrieEntry<K, V> current = entry;
      Cursor.SelectStatus ret = cursor.select(current);
      entry = nextEntry(current);
      switch (ret) {
        case EXIT:
          return current;
        case REMOVE:
          removeEntry(current);
          break; // out of switch, stay in while loop
        case REMOVE_AND_EXIT:
          Map.Entry<K, V> value = new TrieEntry<K, V>(current.getKey(), current.getValue(), -1);
          removeEntry(current);
          return value;
        case CONTINUE: // do nothing.
      }
    }

    return null;
  }

  /**
   * Returns true if 'next' is a valid uplink coming from 'from'.
   */
  private boolean isValidUplink(TrieEntry<K, V> next, TrieEntry<K, V> from) {
    return next != null && next.bitIndex <= from.bitIndex && !next.isEmpty();
  }

  /**
   * Returns true if bitIndex is a valid index
   */
  private static boolean isValidBitIndex(int bitIndex) {
    return 0 <= bitIndex && bitIndex <= Integer.MAX_VALUE;
  }

  /**
   * Returns true if bitIndex is a NULL_BIT_KEY
   */
  private static boolean isNullBitKey(int bitIndex) {
    return bitIndex == KeyAnalyzer.NULL_BIT_KEY;
  }

  /**
   * Returns true if bitIndex is a EQUAL_BIT_KEY
   */
  private static boolean isEqualBitKey(int bitIndex) {
    return bitIndex == KeyAnalyzer.EQUAL_BIT_KEY;
  }

  /**
   * Returns the length of the key, or 0 if the key is null.
   */
  private int length(K key) {
    if (key == null) {
      return 0;
    }

    return keyAnalyzer.length(key);
  }

  /**
   * Returns whether or not the given bit on the
   * key is set, or false if the key is null
   */
  private boolean isBitSet(K key, int keyLength, int bitIndex) {
    if (key == null) { // root's might be null!
      return false;
    }
    return keyAnalyzer.isBitSet(key, keyLength, bitIndex);
  }

  /**
   * Utility method for calling
   * keyAnalyzer.bitIndex(key, 0, length(key), foundKey, 0, length(foundKey))
   */
  private int bitIndex(K key, K foundKey) {
    return keyAnalyzer.bitIndex(key, 0, length(key), foundKey, 0, length(foundKey));
  }

  /**
   * The actual Trie nodes.
   */
  private static class TrieEntry<K, V> implements Map.Entry<K, V>, Serializable {

    private static final long serialVersionUID = 4596023148184140013L;

    private K key;
    private V value;

    /**
     * The index this entry is comparing.
     */
    private int bitIndex;

    /**
     * The parent of this entry.
     */
    private TrieEntry<K, V> parent;
    /**
     * The left child of this entry.
     */
    private TrieEntry<K, V> left;
    /**
     * The right child of this entry.
     */
    private TrieEntry<K, V> right;
    /**
     * The entry who uplinks to this entry.
     */
    private TrieEntry<K, V> predecessor;

    private TrieEntry(K key, V value, int bitIndex) {
      this.key = key;
      this.value = value;

      this.bitIndex = bitIndex;

      this.parent = null;
      this.left = this;
      this.right = null;
      this.predecessor = this;
    }

    public boolean equals(Object o) {
      if (o == this) {
        return true;
      }
      else if (o instanceof Map.Entry) {
        Map.Entry e = (Map.Entry)o;
        Object k1 = getKey();
        Object k2 = e.getKey();
        if (k1 == k2 || (k1 != null && k1.equals(k2))) {
          Object v1 = getValue();
          Object v2 = e.getValue();
          if (v1 == v2 || (v1 != null && v1.equals(v2))) return true;
        }
        return false;
      }
      else {
        return false;
      }
    }

    /**
     * Whether or not the entry is storing a key.
     * Only the root can potentially be empty, all other
     * nodes must have a key.
     */
    public boolean isEmpty() {
      return key == null;
    }

    public K getKey() {
      return key;
    }

    public V getValue() {
      return value;
    }

    public V setValue(V value) {
      V o = this.value;
      this.value = value;
      return o;
    }

    /**
     * Replaces the old key and value with the new ones.
     * Returns the old vlaue.
     */
    private V setKeyValue(K key, V value) {
      this.key = key;
      return setValue(value);
    }

    /**
     * Neither the left nor right child is a loopback
     */
    private boolean isInternalNode() {
      return left != this && right != this;
    }

    /**
     * Either the left or right child is a loopback
     */
    private boolean isExternalNode() {
      return !isInternalNode();
    }

    public String toString() {
      StringBuilder buffer = new StringBuilder();

      if (bitIndex == -1) {
        buffer.append("RootEntry(");
      }
      else {
        buffer.append("Entry(");
      }

      buffer.append("key=").append(getKey()).append(" [").append(bitIndex).append("], ");
      buffer.append("value=").append(getValue()).append(", ");
      //buffer.append("bitIndex=").append(bitIndex).append(", ");

      if (parent != null) {
        if (parent.bitIndex == -1) {
          buffer.append("parent=").append("ROOT");
        }
        else {
          buffer.append("parent=").append(parent.getKey()).append(" [").append(parent.bitIndex).append("]");
        }
      }
      else {
        buffer.append("parent=").append("null");
      }
      buffer.append(", ");

      if (left != null) {
        if (left.bitIndex == -1) {
          buffer.append("left=").append("ROOT");
        }
        else {
          buffer.append("left=").append(left.getKey()).append(" [").append(left.bitIndex).append("]");
        }
      }
      else {
        buffer.append("left=").append("null");
      }
      buffer.append(", ");

      if (right != null) {
        if (right.bitIndex == -1) {
          buffer.append("right=").append("ROOT");
        }
        else {
          buffer.append("right=").append(right.getKey()).append(" [").append(right.bitIndex).append("]");
        }
      }
      else {
        buffer.append("right=").append("null");
      }
      buffer.append(", ");

      if (predecessor != null) {
        if (predecessor.bitIndex == -1) {
          buffer.append("predecessor=").append("ROOT");
        }
        else {
          buffer.append("predecessor=").append(predecessor.getKey()).append(" [").append(predecessor.bitIndex).append("]");
        }
      }

      buffer.append(")");
      return buffer.toString();
    }
  }

  /**
   * Defines the interface to analyze {@link Trie} keys on a bit
   * level. <code>KeyAnalyzer</code>'s
   * methods return the length of the key in bits, whether or not a bit is
   * set, and bits per element in the key.
   * <p/>
   * Additionally, a method determines if a key is a prefix of another key and
   * returns the bit index where one key is different from another key (if
   * the key and found key are equal than the return value is EQUAL_BIT_KEY).
   * <p/>
   * <code>KeyAnalyzer</code> defines:<br>
   * <table cellspace="5">
   * <tr><td>NULL_BIT_KEY</td><td>When key's bits are all zero</td></tr>
   * <tr><td> EQUAL_BIT_KEY </td><td>When keys are the same </td></tr>
   * </table>
   */
  public static interface KeyAnalyzer<K> extends Comparator<K>, Serializable {

    /**
     * Returned by bitIndex if key's bits are all 0
     */
    public static final int NULL_BIT_KEY = -1;

    /**
     * Returned by bitIndex if key and found key are
     * equal. This is a very very specific case and
     * shouldn't happen on a regular basis
     */
    public static final int EQUAL_BIT_KEY = -2;

    /**
     * Returns the length of the Key in bits.
     */
    public int length(K key);

    /**
     * Returns whether or not a bit is set
     */
    public boolean isBitSet(K key, int keyLength, int bitIndex);

    /**
     * Returns the n-th different bit between key and found.
     * This starts the comparison in key at 'keyStart' and goes
     * for 'keyLength' bits, and compares to the found key
     * starting at 'foundStart' and going for 'foundLength' bits.
     */
    public int bitIndex(K key, int keyStart, int keyLength, K found, int foundStart, int foundLength);

    /**
     * Returns the number of bits per element in the key.
     * This is only useful for variable-length keys, such as Strings.
     */
    public int bitsPerElement();

    /**
     * Determines whether or not the given prefix (from offset to length)
     * is a prefix of the given key.
     */
    public boolean isPrefix(K prefix, int offset, int length, K key);
  }

  /**
   * An iterator that stores a single TrieEntry.
   */
  private class SingletonIterator implements Iterator<Map.Entry<K, V>> {
    private final TrieEntry<K, V> entry;
    private int hit = 0;

    public SingletonIterator(TrieEntry<K, V> entry) {
      this.entry = entry;
    }

    public boolean hasNext() {
      return hit == 0;
    }

    public Map.Entry<K, V> next() {
      if (hit != 0) throw new NoSuchElementException();
      hit++;
      return entry;
    }

    public void remove() {
      if (hit != 1) throw new IllegalStateException();
      hit++;
      PatriciaTrie.this.removeEntry(entry);
    }

  }

  /**
   * An iterator for the entries.
   */
  private abstract class NodeIterator<E> implements Iterator<E> {
    protected int expectedModCount = modCount;   // For fast-fail
    protected TrieEntry<K, V> next; // the next node to return
    protected TrieEntry<K, V> current; // the current entry we're on

    // Starts iteration from the beginning.
    protected NodeIterator() {
      next = PatriciaTrie.this.nextEntry(null);
    }

    // Starts iteration at the given entry.
    protected NodeIterator(TrieEntry<K, V> firstEntry) {
      next = firstEntry;
    }

    public boolean hasNext() {
      return next != null;
    }

    TrieEntry<K, V> nextEntry() {
      if (modCount != expectedModCount) throw new ConcurrentModificationException();
      TrieEntry<K, V> e = next;
      if (e == null) throw new NoSuchElementException();

      next = findNext(e);
      current = e;
      return e;
    }

    protected TrieEntry<K, V> findNext(TrieEntry<K, V> prior) {
      return PatriciaTrie.this.nextEntry(prior);
    }

    public void remove() {
      if (current == null) throw new IllegalStateException();
      if (modCount != expectedModCount) throw new ConcurrentModificationException();

      TrieEntry<K, V> node = current;
      current = null;
      PatriciaTrie.this.removeEntry(node);

      expectedModCount = modCount;
    }
  }

  private class ValueIterator extends NodeIterator<V> {
    public V next() {
      return nextEntry().value;
    }
  }

  private class KeyIterator extends NodeIterator<K> {
    public K next() {
      return nextEntry().getKey();
    }
  }

  private class EntryIterator extends NodeIterator<Map.Entry<K, V>> {
    public Map.Entry<K, V> next() {
      return nextEntry();
    }
  }

  /**
   * An iterator for iterating over a prefix search.
   */
  private class PrefixEntryIterator extends NodeIterator<Map.Entry<K, V>> {
    // values to reset the subtree if we remove it.
    protected final K prefix;
    protected final int offset;
    protected final int length;
    protected boolean lastOne;

    protected TrieEntry<K, V> subtree; // the subtree to search within

    // Starts iteration at the given entry & search only within the given subtree.
    PrefixEntryIterator(TrieEntry<K, V> startScan, K prefix, int offset, int length) {
      subtree = startScan;
      next = PatriciaTrie.this.followLeft(startScan);
      this.prefix = prefix;
      this.offset = offset;
      this.length = length;
    }

    public Map.Entry<K, V> next() {
      Map.Entry<K, V> entry = nextEntry();
      if (lastOne) next = null;
      return entry;
    }

    @Override
    protected TrieEntry<K, V> findNext(TrieEntry<K, V> prior) {
      return PatriciaTrie.this.nextEntryInSubtree(prior, subtree);
    }

    @Override
    public void remove() {
      // If the current entry we're removing is the subtree
      // then we need to find a new subtree parent.
      boolean needsFixing = false;
      int bitIdx = subtree.bitIndex;
      if (current == subtree) needsFixing = true;

      super.remove();

      // If the subtree changed its bitIndex or we
      // removed the old subtree, get a new one.
      if (bitIdx != subtree.bitIndex || needsFixing) subtree = subtree(prefix, offset, length);

      // If the subtree's bitIndex is less than the
      // length of our prefix, it's the last item
      // in the prefix tree.
      if (length >= subtree.bitIndex) lastOne = true;
    }

  }

  /**
   * An iterator for submaps.
   */
  private class SubMapEntryIterator extends NodeIterator<Map.Entry<K, V>> {
    private final K firstExcludedKey;

    SubMapEntryIterator(TrieEntry<K, V> first, TrieEntry<K, V> firstExcluded) {
      super(first);
      firstExcludedKey = (firstExcluded == null ? null : firstExcluded.key);
    }

    public boolean hasNext() {
      return next != null && next.key != firstExcludedKey;
    }

    public Map.Entry<K, V> next() {
      if (next == null || next.key == firstExcludedKey) throw new NoSuchElementException();
      return nextEntry();
    }
  }

  Iterator<K> newKeyIterator() {
    return new KeyIterator();
  }

  Iterator<V> newValueIterator() {
    return new ValueIterator();
  }

  Iterator<Map.Entry<K, V>> newEntryIterator() {
    return new EntryIterator();
  }

  /**
   * Each of these fields are initialized to contain an instance of the
   * appropriate view the first time this view is requested.  The views are
   * stateless, so there's no reason to create more than one of each.
   */
  private transient volatile Set<K> keySet = null;
  private transient volatile Collection<V> values = null;
  private transient volatile Set<Map.Entry<K, V>> entrySet = null;

  private class EntrySet extends AbstractSet<Map.Entry<K, V>> {
    public Iterator<Map.Entry<K, V>> iterator() {
      return newEntryIterator();
    }

    public boolean contains(Object o) {
      if (!(o instanceof Map.Entry)) return false;

      TrieEntry<K, V> candidate = getEntry(((Map.Entry)o).getKey());
      return candidate != null && candidate.equals(o);
    }

    public boolean remove(Object o) {
      int size = size();
      PatriciaTrie.this.remove(o);
      return size != size();
    }

    public int size() {
      return size;
    }

    public void clear() {
      PatriciaTrie.this.clear();
    }
  }

  /**
   * Returns a set view of the keys contained in this map.  The set is
   * backed by the map, so changes to the map are reflected in the set, and
   * vice-versa.  The set supports element removal, which removes the
   * corresponding mapping from this map, via the <tt>Iterator.remove</tt>,
   * <tt>Set.remove</tt>, <tt>removeAll</tt>, <tt>retainAll</tt>, and
   * <tt>clear</tt> operations.  It does not support the <tt>add</tt> or
   * <tt>addAll</tt> operations.
   *
   * @return a set view of the keys contained in this map.
   */
  public Set<K> keySet() {
    Set<K> ks = keySet;
    return (ks != null ? ks : (keySet = new KeySet()));
  }

  private class KeySet extends AbstractSet<K> {
    public Iterator<K> iterator() {
      return newKeyIterator();
    }

    public int size() {
      return size;
    }

    public boolean contains(Object o) {
      return containsKey(o);
    }

    public boolean remove(Object o) {
      int size = size();
      PatriciaTrie.this.remove(o);
      return size != size();
    }

    public void clear() {
      PatriciaTrie.this.clear();
    }
  }

  /**
   * Returns a collection view of the values contained in this map. The
   * collection is backed by the map, so changes to the map are reflected in
   * the collection, and vice-versa. The collection supports element
   * removal, which removes the corresponding mapping from this map, via the
   * <tt>Iterator.remove</tt>, <tt>Collection.remove</tt>,
   * <tt>removeAll</tt>, <tt>retainAll</tt>, and <tt>clear</tt> operations.
   * It does not support the <tt>add</tt> or <tt>addAll</tt> operations.
   *
   * @return a collection view of the values contained in this map.
   */
  public Collection<V> values() {
    Collection<V> vs = values;
    return (vs != null ? vs : (values = new Values()));
  }

  /**
   * Test two values for equality.  Works with null values.
   */
  private static boolean valEquals(Object o1, Object o2) {
    return (o1 == null ? o2 == null : o1.equals(o2));
  }

  private class Values extends AbstractCollection<V> {
    public Iterator<V> iterator() {
      return newValueIterator();
    }

    public int size() {
      return size;
    }

    public boolean contains(Object o) {
      return containsValue(o);
    }

    public void clear() {
      PatriciaTrie.this.clear();
    }

    public boolean remove(Object o) {
      for (Iterator<V> i = iterator(); i.hasNext();) {
        V v = i.next();
        if (valEquals(v, o)) {
          i.remove();
          return true;
        }
      }
      return false;
    }
  }

  /**
   * Returns the first entry the Trie is storing.
   * <p/>
   * This is implemented by going always to the left until
   * we encounter a valid uplink. That uplink is the first key.
   */
  private TrieEntry<K, V> firstEntry() {
    // if Trie is empty, no first node.
    if (isEmpty()) return null;

    return followLeft(root);
  }

  /**
   * Goes left through the tree until it finds a valid node.
   */
  private TrieEntry<K, V> followLeft(TrieEntry<K, V> node) {
    while (true) {
      TrieEntry<K, V> child = node.left;
      // if we hit root and it didn't have a node, go right instead.
      if (child.isEmpty()) child = node.right;

      if (child.bitIndex <= node.bitIndex) return child;

      node = child;
    }
  }

  /**
   * Returns the last entry the Trie is storing.
   * <p/>
   * This is implemented by going always to the right until
   * we encounter a valid uplink. That uplink is the last key.
   */
  private TrieEntry<K, V> lastEntry() {
    return followRight(root.left);
  }

  /**
   * Traverses down the right path until it finds an uplink.
   */
  protected TrieEntry<K, V> followRight(TrieEntry<K, V> node) {
    // if Trie is empty, no last entry.
    if (node.right == null) return null;

    // Go as far right as possible, until we encounter an uplink.
    while (node.right.bitIndex > node.bitIndex) node = node.right;

    return node.right;
  }

  public K firstKey() {
    return firstEntry().getKey();
  }

  public SortedMap<K, V> headMap(K toKey) {
    return new SubMap(null, toKey);
  }

  public K lastKey() {
    TrieEntry<K, V> entry = lastEntry();
    if (entry != null) {
      return entry.getKey();
    }
    else {
      return null;
    }
  }


  public SortedMap<K, V> subMap(K fromKey, K toKey) {
    return new SubMap(fromKey, toKey);
  }

  public SortedMap<K, V> tailMap(K fromKey) {
    return new SubMap(fromKey, null);
  }

  /**
   * Returns an entry strictly higher than the given key,
   * or null if no such entry exists.
   */
  protected TrieEntry<K, V> higherEntry(K key) {
    // TODO: Cleanup so that we don't actually have to add/remove from the
    //       tree.  (We do it here because there are other well-defined
    //       functions to perform the search.)
    int keyLength = length(key);

    if (keyLength == 0) {
      if (!root.isEmpty()) {
        // If data in root, and more after -- return it.
        if (size() > 1) {
          return nextEntry(root);
        }
        else { // If no more after, no higher entry.
          return null;
        }
      }
      else {
        // Root is empty & we want something after empty, return first.
        return firstEntry();
      }
    }

    TrieEntry<K, V> found = getNearestEntryForKey(key, keyLength);
    if (key.equals(found.key)) return nextEntry(found);

    int bitIndex = bitIndex(key, found.key);
    if (isValidBitIndex(bitIndex)) {
      TrieEntry<K, V> added = new TrieEntry<K, V>(key, null, bitIndex);
      addEntry(added, keyLength);
      incrementSize(); // must increment because remove will decrement
      TrieEntry<K, V> ceil = nextEntry(added);
      removeEntry(added);
      modCount -= 2; // we didn't really modify it.
      return ceil;
    }
    else if (isNullBitKey(bitIndex)) {
      if (!root.isEmpty()) {
        return firstEntry();
      }
      else if (size() > 1) {
        return nextEntry(firstEntry());
      }
      else {
        return null;
      }
    }
    else if (isEqualBitKey(bitIndex)) {
      return nextEntry(found);
    }

    // we should have exited above.
    throw new IllegalStateException("invalid lookup: " + key);
  }

  /**
   * Returns a key-value mapping associated with the least key greater
   * than or equal to the given key, or null if there is no such key.
   */
  protected TrieEntry<K, V> ceilingEntry(K key) {
    // Basically:
    // Follow the steps of adding an entry, but instead...
    //
    // - If we ever encounter a situation where we found an equal
    //   key, we return it immediately.
    //
    // - If we hit an empty root, return the first iterable item.
    //
    // - If we have to add a new item, we temporarily add it,
    //   find the successor to it, then remove the added item.
    //
    // These steps ensure that the returned value is either the
    // entry for the key itself, or the first entry directly after
    // the key.

    // TODO: Cleanup so that we don't actually have to add/remove from the
    //       tree.  (We do it here because there are other well-defined
    //       functions to perform the search.)
    int keyLength = length(key);

    if (keyLength == 0) {
      if (!root.isEmpty()) {
        return root;
      }
      else {
        return firstEntry();
      }
    }

    TrieEntry<K, V> found = getNearestEntryForKey(key, keyLength);
    if (key.equals(found.key)) return found;

    int bitIndex = bitIndex(key, found.key);
    if (isValidBitIndex(bitIndex)) {
      TrieEntry<K, V> added = new TrieEntry<K, V>(key, null, bitIndex);
      addEntry(added, keyLength);
      incrementSize(); // must increment because remove will decrement
      TrieEntry<K, V> ceil = nextEntry(added);
      removeEntry(added);
      modCount -= 2; // we didn't really modify it.
      return ceil;
    }
    else if (isNullBitKey(bitIndex)) {
      if (!root.isEmpty()) {
        return root;
      }
      else {
        return firstEntry();
      }
    }
    else if (isEqualBitKey(bitIndex)) {
      return found;
    }

    // we should have exited above.
    throw new IllegalStateException("invalid lookup: " + key);
  }

  /**
   * Returns a key-value mapping associated with the greatest key
   * strictly less than the given key, or null if there is no such key.
   */
  protected TrieEntry<K, V> lowerEntry(K key) {
    // Basically:
    // Follow the steps of adding an entry, but instead...
    //
    // - If we ever encounter a situation where we found an equal
    //   key, we return it's previousEntry immediately.
    //
    // - If we hit root (empty or not), return null.
    //
    // - If we have to add a new item, we temporarily add it,
    //   find the previousEntry to it, then remove the added item.
    //
    // These steps ensure that the returned value is always just before
    // the key or null (if there was nothing before it).

    // TODO: Cleanup so that we don't actually have to add/remove from the
    //       tree.  (We do it here because there are other well-defined
    //       functions to perform the search.)
    int keyLength = length(key);

    if (keyLength == 0) {
      return null; // there can never be anything before root.
    }

    TrieEntry<K, V> found = getNearestEntryForKey(key, keyLength);
    if (key.equals(found.key)) return previousEntry(found);

    int bitIndex = bitIndex(key, found.key);
    if (isValidBitIndex(bitIndex)) {
      TrieEntry<K, V> added = new TrieEntry<K, V>(key, null, bitIndex);
      addEntry(added, keyLength);
      incrementSize(); // must increment because remove will decrement
      TrieEntry<K, V> prior = previousEntry(added);
      removeEntry(added);
      modCount -= 2; // we didn't really modify it.
      return prior;
    }
    else if (isNullBitKey(bitIndex)) {
      return null;
    }
    else if (isEqualBitKey(bitIndex)) {
      return previousEntry(found);
    }

    // we should have exited above.
    throw new IllegalStateException("invalid lookup: " + key);
  }

  /**
   * Returns a key-value mapping associated with the greatest key
   * less than or equal to the given key, or null if there is no such key.
   */
  protected TrieEntry<K, V> floorEntry(K key) {
    // TODO: Cleanup so that we don't actually have to add/remove from the
    //       tree.  (We do it here because there are other well-defined
    //       functions to perform the search.)
    int keyLength = length(key);

    if (keyLength == 0) {
      if (!root.isEmpty()) {
        return root;
      }
      else {
        return null;
      }
    }

    TrieEntry<K, V> found = getNearestEntryForKey(key, keyLength);
    if (key.equals(found.key)) return found;

    int bitIndex = bitIndex(key, found.key);
    if (isValidBitIndex(bitIndex)) {
      TrieEntry<K, V> added = new TrieEntry<K, V>(key, null, bitIndex);
      addEntry(added, keyLength);
      incrementSize(); // must increment because remove will decrement
      TrieEntry<K, V> floor = previousEntry(added);
      removeEntry(added);
      modCount -= 2; // we didn't really modify it.
      return floor;
    }
    else if (isNullBitKey(bitIndex)) {
      if (!root.isEmpty()) {
        return root;
      }
      else {
        return null;
      }
    }
    else if (isEqualBitKey(bitIndex)) {
      return found;
    }

    // we should have exited above.
    throw new IllegalStateException("invalid lookup: " + key);
  }


  /**
   * Finds the subtree that contains the prefix.
   * <p/>
   * This is very similar to getR but with the difference that
   * we stop the lookup if h.bitIndex > keyLength.
   */
  private TrieEntry<K, V> subtree(K prefix, int offset, int length) {
    TrieEntry<K, V> current = root.left;
    TrieEntry<K, V> path = root;
    while (true) {
      if (current.bitIndex <= path.bitIndex || length < current.bitIndex) break;

      path = current;
      if (!isBitSet(prefix, length + offset, current.bitIndex + offset)) {
        current = current.left;
      }
      else {
        current = current.right;
      }
    }

    // Make sure the entry is valid for a subtree.
    TrieEntry<K, V> entry = current.isEmpty() ? path : current;

    // If entry is root, it can't be empty.
    if (entry.isEmpty()) return null;

    int offsetLength = offset + length;

    // if root && length of root is less than length of lookup,
    // there's nothing.
    // (this prevents returning the whole subtree if root has an empty
    //  string and we want to lookup things with "\0")
    if (entry == root && length(entry.getKey()) < offsetLength) return null;

    // Found key's length-th bit differs from our key
    // which means it cannot be the prefix...
    if (isBitSet(prefix, offsetLength, offsetLength) != isBitSet(entry.key, length(entry.key), length)) {
      return null;
    }

    // ... or there are less than 'length' equal bits
    int bitIndex = keyAnalyzer.bitIndex(prefix, offset, length, entry.key, 0, length(entry.getKey()));
    if (bitIndex >= 0 && bitIndex < length) return null;

    return entry;
  }

  /**
   * A submap used for prefix views over the Trie.
   */
  private class PrefixSubMap extends SubMap {
    protected final K prefix;
    protected final int offset;
    protected final int length;
    private transient int keyModCount = 0;
    protected int size;

    PrefixSubMap(K prefix, int offset, int length) {
      this.prefix = prefix;
      this.offset = offset;
      this.length = length;
      fromInclusive = false;
    }

    public K firstKey() {
      fixup();
      TrieEntry<K, V> e;
      if (fromKey == null) {
        e = firstEntry();
      }
      else {
        e = higherEntry(fromKey);
      }

      K first = e != null ? e.getKey() : null;
      if (e == null || !keyAnalyzer.isPrefix(prefix, offset, length, first)) throw new NoSuchElementException();
      return first;
    }

    public K lastKey() {
      fixup();
      TrieEntry<K, V> e;
      if (toKey == null) {
        e = lastEntry();
      }
      else {
        e = lowerEntry(toKey);
      }

      K last = e != null ? e.getKey() : null;
      if (e == null || !keyAnalyzer.isPrefix(prefix, offset, length, last)) throw new NoSuchElementException();
      return last;
    }

    protected boolean inRange(K key) {
      return keyAnalyzer.isPrefix(prefix, offset, length, key);
    }

    protected boolean inRange2(K key) {
      return keyAnalyzer.isPrefix(prefix, offset, length, key);
    }

    protected boolean inToRange(K key, boolean forceInclusive) {
      return keyAnalyzer.isPrefix(prefix, offset, length, key);
    }

    protected boolean inFromRange(K key, boolean forceInclusive) {
      return keyAnalyzer.isPrefix(prefix, offset, length, key);
    }

    private void fixup() {
      // The trie has changed since we last
      // found our toKey / fromKey
      if (modCount != keyModCount) {
        Iterator<Map.Entry<K, V>> iter = entrySet().iterator();
        size = 0;

        Map.Entry<K, V> entry = null;
        if (iter.hasNext()) {
          entry = iter.next();
          size = 1;
        }

        fromKey = entry == null ? null : entry.getKey();
        if (fromKey != null) {
          TrieEntry<K, V> prior = previousEntry((TrieEntry<K, V>)entry);
          fromKey = prior == null ? null : prior.getKey();
        }

        toKey = fromKey;

        while (iter.hasNext()) {
          size++;
          entry = iter.next();
        }

        toKey = entry == null ? null : entry.getKey();

        if (toKey != null) {
          entry = nextEntry((TrieEntry<K, V>)entry);
          toKey = entry == null ? null : entry.getKey();
        }

        keyModCount = modCount;
      }
    }

    protected Set<Map.Entry<K, V>> newSubMapEntrySet() {
      return new PrefixEntrySetView();
    }

    private class PrefixEntrySetView extends SubMap.EntrySetView {
      private TrieEntry<K, V> prefixStart;
      private int iterModCount = 0;

      public int size() {
        fixup();
        return PrefixSubMap.this.size;
      }

      @SuppressWarnings("unchecked")
      public Iterator<Map.Entry<K, V>> iterator() {
        if (modCount != iterModCount) {
          prefixStart = subtree(prefix, offset, length);
          iterModCount = modCount;
        }

        if (prefixStart == null) {
          return new EmptyIterator();
        }
        else if (length >= prefixStart.bitIndex) {
          return new SingletonIterator(prefixStart);
        }
        else {
          return new PrefixEntryIterator(prefixStart, prefix, offset, length);
        }
      }
    }
  }

  private class SubMap extends AbstractMap<K, V> implements SortedMap<K, V>, java.io.Serializable {

    // TODO: add serialVersionUID

    /**
     * The key to start from, null if the beginning.
     */
    protected K fromKey;

    /**
     * The key to end at, null if till the end.
     */
    protected K toKey;

    /**
     * Whether or not the 'from' is inclusive.
     */
    protected boolean fromInclusive;

    /**
     * Whether or not the 'to' is inclusive.
     */
    protected boolean toInclusive;

    /**
     * Constructs a blank SubMap -- this should ONLY be used
     * by subclasses that wish to lazily construct their
     * fromKey or toKey
     */
    protected SubMap() {
    }

    SubMap(K fromKey, K toKey) {
      if (fromKey == null && toKey == null) throw new IllegalArgumentException("must have a from or to!");
      if (fromKey != null && toKey != null && keyAnalyzer.compare(fromKey, toKey) > 0) {
        throw new IllegalArgumentException("fromKey > toKey");
      }
      this.fromKey = fromKey;
      this.toKey = toKey;
      fromInclusive = true;
    }

    public boolean isEmpty() {
      return entrySet().isEmpty();
    }

    @SuppressWarnings("unchecked")
    public boolean containsKey(Object key) {
      return inRange((K)key) && PatriciaTrie.this.containsKey(key);
    }

    @SuppressWarnings("unchecked")
    public V remove(Object key) {
      if (!inRange((K)key)) return null;
      return PatriciaTrie.this.remove(key);
    }

    @SuppressWarnings("unchecked")
    public V get(Object key) {
      if (!inRange((K)key)) return null;
      return PatriciaTrie.this.get(key);
    }

    public V put(K key, V value) {
      if (!inRange(key)) throw new IllegalArgumentException("key out of range");
      return PatriciaTrie.this.put(key, value);
    }

    public Comparator<? super K> comparator() {
      return keyAnalyzer;
    }

    public K firstKey() {
      TrieEntry<K, V> e;
      if (fromKey == null) {
        e = firstEntry();
      }
      else {
        if (fromInclusive) {
          e = ceilingEntry(fromKey);
        }
        else {
          e = higherEntry(fromKey);
        }
      }

      K first = e != null ? e.getKey() : null;
      if (e == null || toKey != null && !inToRange(first, false)) throw new NoSuchElementException();
      return first;
    }

    public K lastKey() {
      TrieEntry<K, V> e;
      if (toKey == null) {
        e = lastEntry();
      }
      else {
        if (toInclusive) {
          e = floorEntry(toKey);
        }
        else {
          e = lowerEntry(toKey);
        }
      }

      K last = e != null ? e.getKey() : null;
      if (e == null || fromKey != null && !inFromRange(last, false)) throw new NoSuchElementException();
      return last;
    }

    private transient Set<Map.Entry<K, V>> entrySet;

    public Set<Map.Entry<K, V>> entrySet() {
      if (entrySet == null) entrySet = newSubMapEntrySet();
      return entrySet;
    }

    protected Set<Map.Entry<K, V>> newSubMapEntrySet() {
      return new EntrySetView();
    }

    class EntrySetView extends AbstractSet<Map.Entry<K, V>> {
      private transient int size = -1, sizeModCount;

      public int size() {
        if (size == -1 || sizeModCount != PatriciaTrie.this.modCount) {
          size = 0;
          sizeModCount = PatriciaTrie.this.modCount;
          Iterator i = iterator();
          while (i.hasNext()) {
            size++;
            i.next();
          }
        }
        return size;
      }

      public boolean isEmpty() {
        return !iterator().hasNext();
      }

      @SuppressWarnings("unchecked")
      public boolean contains(Object o) {
        if (!(o instanceof Map.Entry)) return false;
        Map.Entry<K, V> entry = (Map.Entry<K, V>)o;
        K key = entry.getKey();
        if (!inRange(key)) return false;
        TrieEntry<K, V> node = getEntry(key);
        return node != null && valEquals(node.getValue(), entry.getValue());
      }

      @SuppressWarnings("unchecked")
      public boolean remove(Object o) {
        if (!(o instanceof Map.Entry)) return false;
        Map.Entry<K, V> entry = (Map.Entry<K, V>)o;
        K key = entry.getKey();
        if (!inRange(key)) return false;
        TrieEntry<K, V> node = getEntry(key);
        if (node != null && valEquals(node.getValue(), entry.getValue())) {
          removeEntry(node);
          return true;
        }
        return false;
      }

      public Iterator<Map.Entry<K, V>> iterator() {
        return new SubMapEntryIterator((fromKey == null ? firstEntry() : ceilingEntry(fromKey)),
                                       (toKey == null ? null : ceilingEntry(toKey)));
      }
    }

    public SortedMap<K, V> subMap(K fromKey, K toKey) {
      if (!inRange2(fromKey)) throw new IllegalArgumentException("fromKey out of range");
      if (!inRange2(toKey)) throw new IllegalArgumentException("toKey out of range");
      return new SubMap(fromKey, toKey);
    }

    public SortedMap<K, V> headMap(K toKey) {
      if (!inRange2(toKey)) throw new IllegalArgumentException("toKey out of range");
      return new SubMap(fromKey, toKey);
    }

    public SortedMap<K, V> tailMap(K fromKey) {
      if (!inRange2(fromKey)) throw new IllegalArgumentException("fromKey out of range");
      return new SubMap(fromKey, toKey);
    }

    protected boolean inRange(K key) {
      return (fromKey == null || inFromRange(key, false)) && (toKey == null || inToRange(key, false));
    }

    // This form allows the high endpoint (as well as all legit keys)
    protected boolean inRange2(K key) {
      return (fromKey == null || inFromRange(key, false)) && (toKey == null || inToRange(key, true));
    }

    protected boolean inToRange(K key, boolean forceInclusive) {
      int ret = keyAnalyzer.compare(key, toKey);
      if (toInclusive || forceInclusive) {
        return ret <= 0;
      }
      else {
        return ret < 0;
      }
    }

    protected boolean inFromRange(K key, boolean forceInclusive) {
      int ret = keyAnalyzer.compare(key, fromKey);
      if (fromInclusive || forceInclusive) {
        return ret >= 0;
      }
      else {
        return ret > 0;
      }
    }
  }

  private static class EmptyIterator implements Iterator {
    public boolean hasNext() {
      return false;
    }

    public Object next() {
      throw new NoSuchElementException();
    }

    public void remove() {
      throw new IllegalStateException();
    }

  }
}

