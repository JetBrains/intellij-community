package com.intellij.slicer;

import javax.swing.*;

/**
 * @author cdr
 */
public interface MyColoredTreeCellRenderer {
  void customizeCellRenderer(SliceUsageCellRenderer renderer,
                             JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus);
}
