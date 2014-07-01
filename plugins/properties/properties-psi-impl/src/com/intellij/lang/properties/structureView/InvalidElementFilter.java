package com.intellij.lang.properties.structureView;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.ActionPresentationData;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.lang.properties.editor.ResourceBundlePropertyStructureViewElement;
import org.jetbrains.annotations.NotNull;

/**
 *  Filter which only shows elements that are invalid.
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
    return new ActionPresentationData("Invalid items", "Filter invalid items", AllIcons.Nodes.PpInvalid);
  }

  @NotNull
  @Override
  public String getName() {
    return "Invalid icons";
  }
}
