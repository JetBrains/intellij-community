package com.intellij.openapi.vcs.update;

import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.openapi.vcs.update.AbstractTreeNode;

import javax.swing.*;

/**
 * author: lesya
 */
public class UpdateTreeCellRenderer extends ColoredTreeCellRenderer{

  public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {

    AbstractTreeNode treeNode = (AbstractTreeNode)value;
    append(treeNode.getText(), treeNode.getAttributes());
    setIcon(treeNode.getIcon(expanded));
  }
}
