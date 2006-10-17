package com.intellij.ide.util.treeView.smartTree;

import com.intellij.ide.util.treeView.AbstractTreeStructure;

import java.util.HashSet;

/**
 * @author Maxim.Mossienko
 */
public class TreeStructureUtil {
  private TreeStructureUtil() {}

  public static Object[] getChildElementsFromTreeStructure(AbstractTreeStructure treeStructure, Object element) {
    final Object[] items = treeStructure.getChildElements(element);
    HashSet<Object> viewedItems = new HashSet<Object>();

    for (Object item : items) {
      if (viewedItems.contains(item)) continue;
      viewedItems.add(item);
    }

    return items;
  }
}
