package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.ActionPresentationData;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.Icons;

public class PublicElementsFilter implements Filter{
  public static final String ID = "SHOW_NON_PUBLIC";

  public boolean isVisible(TreeElement treeNode) {
    if (treeNode instanceof JavaClassTreeElementBase) {
      return ((JavaClassTreeElementBase)treeNode).isPublic();
    }
    else {
      return true;
    }
  }

  public ActionPresentation getPresentation() {
    return new ActionPresentationData("Show non-public", null, Icons.PRIVATE_ICON);
  }

  public String getName() {
    return ID;
  }

  public boolean isReverted() {
    return true;
  }
}
