package com.intellij.lang.properties.structureView;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.ActionPresentationData;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.lang.properties.editor.ResourceBundlePropertyStructureViewElement;
import org.jetbrains.annotations.NotNull;

/**
 *  Filter which only shows elements in properties structure view that are invalid (have missing translations).
 */
public class InvalidElementFilter implements Filter {
  @Override
  public boolean isVisible(TreeElement treeNode) {
    return !((ResourceBundlePropertyStructureViewElement) treeNode).propertyComplete();
  }

  @Override
  public boolean isReverted() {
    return false;
  }

  @NotNull
  @Override
  public ActionPresentation getPresentation() {
    return new ActionPresentationData(IdeBundle.message("action.propertiesview.filter.invalid"), null, AllIcons.Nodes.ErrorIntroduction);
  }

  @NotNull
  @Override
  public String getName() {
    return "Invalid icons";
  }
}
