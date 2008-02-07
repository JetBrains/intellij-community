package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;

import javax.swing.*;

/**
 * @author nik
 */
public class XDebuggerTreeRenderer extends ColoredTreeCellRenderer {
  public void customizeCellRenderer(final JTree tree,
                                    final Object value,
                                    final boolean selected,
                                    final boolean expanded,
                                    final boolean leaf,
                                    final int row,
                                    final boolean hasFocus) {
    XDebuggerTreeNode node = (XDebuggerTreeNode)value;
    node.getText().appendToComponent(this);
    setIcon(node.getIcon());
  }
}
