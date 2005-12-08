package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.ActionPresentationData;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.Icons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class FieldsFilter implements Filter{
  @NonNls public static final String ID = "SHOW_FIELDS";

  public boolean isVisible(TreeElement treeNode) {
    return !(treeNode instanceof PsiFieldTreeElement);
  }

  @NotNull
  public ActionPresentation getPresentation() {
    return new ActionPresentationData(IdeBundle.message("action.structureview.show.fields"), null, Icons.FIELD_ICON);
  }

  @NotNull
  public String getName() {
    return ID;
  }

  public boolean isReverted() {
    return true;
  }
}
