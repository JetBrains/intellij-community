/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Nov 23, 2001
 * Time: 10:31:03 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.ui;

import com.intellij.psi.PsiElement;
import com.intellij.codeInspection.ex.InspectionToolsPanel;
import com.intellij.openapi.util.TextRange;

import java.util.Comparator;

public class InspectionResultsViewComparator implements Comparator {
  private static InspectionResultsViewComparator ourInstance = null;

  public int compare(Object o1, Object o2) {
    InspectionTreeNode node1 = (InspectionTreeNode)o1;
    InspectionTreeNode node2 = (InspectionTreeNode)o2;

    if (node1 instanceof InspectionNode && node2 instanceof InspectionGroupNode) return -1;
    if (node2 instanceof InspectionNode && node1 instanceof InspectionGroupNode) return 1;

    if (node1 instanceof EntryPointsNode && node2 instanceof InspectionPackageNode) return -1;
    if (node2 instanceof EntryPointsNode && node1 instanceof InspectionPackageNode) return 1;

    if (node1 instanceof InspectionNode && node2 instanceof InspectionNode){
      return InspectionToolsPanel.getDisplayTextToSort(((InspectionNode)node1).toString()).compareToIgnoreCase(InspectionToolsPanel.getDisplayTextToSort(((InspectionNode)node2).toString()));
    }

    if (node1 instanceof RefElementNode && node2 instanceof RefElementNode){
      final PsiElement element1 = ((RefElementNode)node1).getElement().getElement();
      final PsiElement element2 = ((RefElementNode)node2).getElement().getElement();
      if (element1 != null && element2 != null){
        final TextRange textRange1 = element1.getTextRange();
        final TextRange textRange2 = element2.getTextRange();
        if (textRange1 != null && textRange2 != null) {
          return textRange1.getStartOffset() - textRange2.getStartOffset();
        }
      }
    }
    if (node1 instanceof ProblemDescriptionNode && node2 instanceof ProblemDescriptionNode) return 0;

    return node1.toString().compareTo(node2.toString());
  }

  public static InspectionResultsViewComparator getInstance() {
    if (ourInstance == null) {
      ourInstance = new InspectionResultsViewComparator();
    }

    return ourInstance;
  }
}
