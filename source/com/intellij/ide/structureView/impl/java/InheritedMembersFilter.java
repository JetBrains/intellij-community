package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.ActionPresentationData;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.openapi.util.IconLoader;

public class InheritedMembersFilter implements Filter {
  public static final String ID = "SHOW_INHERITED";

  public InheritedMembersFilter() {
  }
  public boolean isVisible(TreeElement treeNode) {
    if (treeNode instanceof JavaClassTreeElementBase) {
      return !((JavaClassTreeElementBase)treeNode).isInherited();
    }
    else {
      return true;
    }
  }

  public ActionPresentation getPresentation() {
    return new ActionPresentationData("Show Inherited", null, IconLoader.getIcon("/hierarchy/supertypes.png"));
  }

  public String getName() {
    return ID;
  }
}
