package com.jetbrains.python.structureView;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.ActionPresentationData;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

/**
 * @author vlan
 */
public class PyFieldsFilter implements Filter {
  private static final String ID = "SHOW_FIELDS";

  @Override
  public boolean isReverted() {
    return true;
  }

  @Override
  public boolean isVisible(TreeElement treeNode) {
    if (treeNode instanceof PyStructureViewElement) {
      final PyStructureViewElement sve = (PyStructureViewElement)treeNode;
      return !sve.isField();
    }
    return true;
  }

  @NotNull
  @Override
  public String getName() {
    return ID;
  }

  @Override
  public String toString() {
    return getName();
  }

  @NotNull
  @Override
  public ActionPresentation getPresentation() {
    return new ActionPresentationData(IdeBundle.message("action.structureview.show.fields"), null, PlatformIcons.FIELD_ICON);
  }
}
