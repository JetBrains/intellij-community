package com.intellij.slicer;

import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.usageView.UsageTreeColors;
import com.intellij.usageView.UsageTreeColorsScheme;
import com.intellij.usageView.UsageViewBundle;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;

/**
 * @author cdr
 */
public class SliceUsageCellRenderer extends ColoredTreeCellRenderer {
  private static final EditorColorsScheme ourColorsScheme = UsageTreeColorsScheme.getInstance().getScheme();
  private static final SimpleTextAttributes ourInvalidAttributes = SimpleTextAttributes.fromTextAttributes(ourColorsScheme.getAttributes(UsageTreeColors.INVALID_PREFIX));

  public SliceUsageCellRenderer() {
    setOpaque(false);
  }

  public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    assert value instanceof DefaultMutableTreeNode;
    DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)value;
    Object userObject = treeNode.getUserObject();
    if (userObject == null) return;
    if (userObject instanceof SliceNode) {
      SliceNode node = (SliceNode)userObject;
      setIcon(node.getPresentation().getIcon(expanded));
      SliceUsage sliceUsage = node.getValue();
      if (node.isValid()) {
        sliceUsage.customizeTreeCellRenderer(this);
        setToolTipText(sliceUsage.getPresentation().getTooltipText());
      }
      else {
        append(UsageViewBundle.message("node.invalid") + " ", ourInvalidAttributes);
      }
    }
    else {
      assert userObject instanceof String : userObject;
      append((String)userObject, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    }
  }
}

