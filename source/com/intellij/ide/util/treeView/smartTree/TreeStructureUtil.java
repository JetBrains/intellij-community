package com.intellij.ide.util.treeView.smartTree;

import com.intellij.ide.util.treeView.AbstractTreeStructure;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Jul 28, 2005
 * Time: 5:55:50 PM
 * To change this template use File | Settings | File Templates.
 */
public class TreeStructureUtil {
  private TreeStructureUtil() {}

  public static Object[] getChildElementsFromTreeStructure(AbstractTreeStructure treeStructure, Object element) {
    final Object[] items = treeStructure.getChildElements(element);
    ArrayList<Object> result = new ArrayList<Object>();
    HashSet<Object> viewedItems = new HashSet<Object>();

    for (int i = 0; i < items.length; i++) {
      if (viewedItems.contains(items[i]))
        continue;
      viewedItems.add(items[i]);
      result.add(items[i]);
    }

    return items;
  }
}
