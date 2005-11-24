package com.intellij.util.containers;

import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.openapi.components.ApplicationComponent;

import java.util.ArrayList;

import org.jetbrains.annotations.NonNls;

public class CharTrie implements ApplicationComponent {
  private static final String ourEmptyString = "";
  private final ArrayList<Node> myAllNodes;

  private class Node {
    private final char myChar;
    private final int myParent;
    private IntArrayList myChildren;

    Node(int parent, char c) {
      myChar = c;
      myParent = parent;
    }
  }

  @NonNls
  public String getComponentName() {
    return "CharTrie";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public CharTrie() {
    myAllNodes = new ArrayList<Node>();
    final Node root = new Node(-1, '\0');
    myAllNodes.add(root);
  }

  /**
   * Returns unique hash code for a string.
   */
  public int getHashCode(String s) {
    int index = 0;
    for (int i = 0; i < s.length(); ++i) {
      index = getSubNode(index, s.charAt(i));
    }
    return index;
  }

  /**
   * Returns string by unique hash code.
   */
  public String getString(int hashCode) {
    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      while (hashCode > 0) {
        final CharTrie.Node node = myAllNodes.get(hashCode);
        builder.append(node.myChar);
        hashCode = node.myParent;
      }
      return (builder.length() == 0) ? ourEmptyString : builder.reverse().toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  /**
   * Returns unique hash code for a reversed string.
   */
  public int getHashCodeForReversedString(String s) {
    int index = 0;
    for (int i = s.length() - 1; i >= 0; --i) {
      index = getSubNode(index, s.charAt(i));
    }
    return index;
  }

  /**
   * Returns reversed string by unique hash code.
   */
  public String getReversedString(int hashCode) {
    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      while (hashCode > 0) {
        final CharTrie.Node node = myAllNodes.get(hashCode);
        builder.append(node.myChar);
        hashCode = node.myParent;
      }
      return (builder.length() == 0) ? ourEmptyString : builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  private int getSubNode(int parentIndex, char c) {

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
      int comp = node.myChar - c;
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
    myAllNodes.add(new Node(parentIndex, c));
    return index;
  }
}