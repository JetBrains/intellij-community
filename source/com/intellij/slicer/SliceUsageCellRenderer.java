package com.intellij.slicer;

import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.usageView.UsageTreeColors;
import com.intellij.usageView.UsageTreeColorsScheme;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.TextChunk;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;

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
    if (!(value instanceof DefaultMutableTreeNode)) {
      assert false;
      //append(value.toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
    DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)value;
    Object userObject = treeNode.getUserObject();
    if (userObject == null) return;
    if (userObject instanceof SliceNode) {
      SliceNode node = (SliceNode)userObject;
      setIcon(node.getPresentation().getIcon(expanded));
      SliceUsage sliceUsage = node.getValue();
      boolean select = sliceUsage.duplicate && !selected;
      if (node.isValid()) {
        TextChunk[] text = node.getValue().getPresentation().getText();
        for (TextChunk textChunk : text) {
          doappend(textChunk.getText(), SimpleTextAttributes.fromTextAttributes(textChunk.getAttributes()), select);
        }

        if (sliceUsage.myContainingParameter != null) {
          doappend(" in " + forMethod(sliceUsage.myContainingMethod, sliceUsage.myContainingParameter), SimpleTextAttributes.GRAY_ATTRIBUTES, select);
        }
      }
      else {
        doappend(UsageViewBundle.message("node.invalid") + " ", ourInvalidAttributes, select);
      }
    }
    else if (userObject instanceof String) {
      append((String)userObject, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    }
    else {
      assert false;
    }
  }

  private void doappend(String s, SimpleTextAttributes attributes, boolean select) {
    SimpleTextAttributes p = new SimpleTextAttributes(select ? Color.CYAN : attributes.getBgColor(), attributes.getFgColor(),
                                                      attributes.getWaveColor(), attributes.getStyle());
    append(s, p);
  }

  private static String forMethod(PsiMethod method, PsiParameter parameter) {
    return PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS,
                                      PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.SHOW_FQ_CLASS_NAMES, 999)
           ;
  }

  public static String getTooltipText(final Object value) {
    if (value instanceof DefaultMutableTreeNode) {
      DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)value;
      Object userObject = treeNode.getUserObject();
      if (userObject instanceof SliceNode) {
        SliceNode node = (SliceNode)userObject;
        return node.getValue().getPresentation().getTooltipText();
      }
    }
    return null;
  }
}

