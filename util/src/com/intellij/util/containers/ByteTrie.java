package com.intellij.util.containers;

import org.jetbrains.annotations.NonNls;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

public class ByteTrie {
  private static final String EMPTY_STRING = "";
  @NonNls private static final String UTF_8_CHARSET_NAME = "UTF-8";
  private final ArrayList<Node> myAllNodes;

  private class Node {
    private final byte myChar;
    private final int myParent;
    private IntArrayList myChildren;

    Node(int parent, byte b) {
      myChar = b;
      myParent = parent;
    }
  }

  public ByteTrie() {
    myAllNodes = new ArrayList<Node>();
    final Node root = new Node(-1, (byte)0);
    myAllNodes.add(root);
  }

  public int size() {
    return myAllNodes.size();
  }

  /**
   * Returns unique hash code for a string.
   *
   * @return negative - an error occured, 0 - no such string in trie, positive - actual hashcode
   */
  public int getHashCode(String s) {
    try {
      return getHashCode(s.getBytes(UTF_8_CHARSET_NAME));
    }
    catch (UnsupportedEncodingException e) {
      return -1;
    }
  }

  /**
   * Returns string by unique hash code.
   */
  public String getString(int hashCode) {
    try {
      return new String(getBytes(hashCode), UTF_8_CHARSET_NAME);
    }
    catch (UnsupportedEncodingException e) {
      return EMPTY_STRING;
    }
  }

  /**
   * Returns unique hash code for a reversed string.
   */
  public int getHashCodeForReversedString(String s) {
    try {
      return getHashCodeForReversedBytes(s.getBytes(UTF_8_CHARSET_NAME));
    }
    catch (UnsupportedEncodingException e) {
      return -1;
    }
  }

  /**
   * Returns reversed string by unique hash code.
   */
  public String getReversedString(int hashCode) {
    try {
      return new String(getReversedBytes(hashCode), UTF_8_CHARSET_NAME);
    }
    catch (UnsupportedEncodingException e) {
      return EMPTY_STRING;
    }
  }

  public int getHashCode(byte[] bytes) {
    return getHashCode(bytes, 0, bytes.length);
  }

  public int getHashCode(byte[] bytes, int offset, int length) {
    int index = 0;
    while (length-- > 0) {
      index = getSubNode(index, bytes[offset++]);
    }
    return index;
  }

  public long getMaximumMatch(byte[] bytes, int offset, int length) {
    int index = 0;
    int resultingLength = 0;
    while (length-- > 0) {
      int nextIndex = searchForSubNode(index, bytes[offset++]);
      if (nextIndex == 0) {
        break;
      }
      index = nextIndex;
      resultingLength++;
    }

    return index + (((long)resultingLength) << 32);
  }

  public byte[] getBytes(int hashCode) {
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    while (hashCode > 0) {
      final ByteTrie.Node node = myAllNodes.get(hashCode);
      writeByte(stream, node.myChar);
      hashCode = node.myParent;
    }
    final byte[] bytes = stream.toByteArray();
    // reverse bytes
    for (int i = 0, j = bytes.length - 1; i < j; ++i, --j) {
      byte swap = bytes[i];
      bytes[i] = bytes[j];
      bytes[j] = swap;
    }
    return bytes;
  }

  public int getHashCodeForReversedBytes(byte[] bytes) {
    return getHashCodeForReversedBytes(bytes, bytes.length - 1, bytes.length);
  }

  public int getHashCodeForReversedBytes(byte[] bytes, int offset, int length) {
    int index = 0;
    while (length-- > 0) {
      index = getSubNode(index, bytes[offset--]);
    }
    return index;
  }

  public byte[] getReversedBytes(int hashCode) {
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    while (hashCode > 0) {
      final ByteTrie.Node node = myAllNodes.get(hashCode);
      writeByte(stream, node.myChar);
      hashCode = node.myParent;
    }
    return stream.toByteArray();
  }

  private int getSubNode(int parentIndex, byte b) {
    Node parentNode = myAllNodes.get(parentIndex);
    if (parentNode.myChildren == null) {
      parentNode.myChildren = new IntArrayList(1);
    }
    IntArrayList children = parentNode.myChildren;
    int left = 0;
    int right = children.size() - 1;
    int middle;
    int index;
    while (left <= right) {
      middle = (left + right) >> 1;
      index = children.get(middle);
      Node node = myAllNodes.get(index);
      int comp = node.myChar - b;
      if (comp == 0) {
        return index;
      }
      if (comp < 0) {
        left = middle + 1;
      }
      else {
        right = middle - 1;
      }
    }
    index = myAllNodes.size();
    children.add(left, index);
    myAllNodes.add(new Node(parentIndex, b));
    return index;
  }

  private int searchForSubNode(int parentIndex, byte b) {
    Node parentNode = myAllNodes.get(parentIndex);
    IntArrayList children = parentNode.myChildren;
    if (children == null) {
      return 0;
    }
    int left = 0;
    int right = children.size() - 1;
    int middle;
    while (left <= right) {
      middle = (left + right) >> 1;
      int index = children.get(middle);
      Node node = myAllNodes.get(index);
      int comp = node.myChar - b;
      if (comp == 0) {
        return index;
      }
      if (comp < 0) {
        left = middle + 1;
      }
      else {
        right = middle - 1;
      }
    }
    return 0;
  }

  void writeByte(ByteArrayOutputStream stream, byte b) {
    int out = b;
    if (out < 0) {
      out += 256;
    }
    stream.write(out);
  }
}