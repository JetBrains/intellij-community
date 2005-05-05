package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.ActionPresentationData;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.Icons;

public class FieldsFilter implements Filter{
  public static final String ID = "SHOW_FIELDS";

  public boolean isVisible(TreeElement treeNode) {
    return !(treeNode instanceof PsiFieldTreeElement);
  }

  public ActionPresentation getPresentation() {
    return new ActionPresentationData("Show Fields", null, Icons.FIELD_ICON);
  }

  public String getName() {
    return ID;
  }

  public boolean isReverted() {
    return true;
  }
}
