/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Nov 23, 2001
 * Time: 10:31:03 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.ui;

import java.util.Comparator;

public class RefAlphabeticalComparator implements Comparator {
  private static RefAlphabeticalComparator ourInstance = null;

  public int compare(Object o1, Object o2) {
    InspectionTreeNode node1 = (InspectionTreeNode)o1;
    InspectionTreeNode node2 = (InspectionTreeNode)o2;

    if (node1 instanceof InspectionNode && node2 instanceof InspectionGroupNode) return -1;
    if (node2 instanceof InspectionNode && node1 instanceof InspectionGroupNode) return 1;

    if (node1 instanceof EntryPointsNode && node2 instanceof InspectionPackageNode) return -1;
    if (node2 instanceof EntryPointsNode && node1 instanceof InspectionPackageNode) return 1;

    return node1.toString().compareTo(node2.toString());
  }

  public static RefAlphabeticalComparator getInstance() {
    if (ourInstance == null) {
      ourInstance = new RefAlphabeticalComparator();
    }

    return ourInstance;
  }
}
