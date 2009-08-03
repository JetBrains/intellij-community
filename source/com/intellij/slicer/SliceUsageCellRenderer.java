package com.intellij.slicer;

import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.usageView.UsageTreeColors;
import com.intellij.usageView.UsageTreeColorsScheme;
import com.intellij.usages.TextChunk;
import com.intellij.usages.UsageInfo2UsageAdapter;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;

/**
 * @author cdr
 */
public class SliceUsageCellRenderer extends ColoredTreeCellRenderer {
  private static final EditorColorsScheme ourColorsScheme = UsageTreeColorsScheme.getInstance().getScheme();
  public static final SimpleTextAttributes ourInvalidAttributes = SimpleTextAttributes.fromTextAttributes(ourColorsScheme.getAttributes(UsageTreeColors.INVALID_PREFIX));

  public SliceUsageCellRenderer() {
    setOpaque(false);
  }

  public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    assert value instanceof DefaultMutableTreeNode;
    DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)value;
    Object userObject = treeNode.getUserObject();
    if (userObject == null) return;
    if (userObject instanceof MyColoredTreeCellRenderer) {
      MyColoredTreeCellRenderer node = (MyColoredTreeCellRenderer)userObject;
      node.customizeCellRenderer(this, tree, value, selected, expanded, leaf, row, hasFocus);
      if (node instanceof SliceNode) {
        setToolTipText(((SliceNode)node).getPresentation().getTooltip());
      }
    }
    else {
      append(userObject.toString(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    }
  }

  public void customizeCellRendererFor(UsageInfo2UsageAdapter sliceUsage) {
    TextChunk[] text = sliceUsage.getPresentation().getText();
    for (TextChunk textChunk : text) {
      append(textChunk.getText(), SimpleTextAttributes.fromTextAttributes(textChunk.getAttributes()));
    }

    PsiElement element = sliceUsage.getElement();
    PsiMethod method;
    PsiClass aClass;
    while (true) {
      method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
      aClass = method == null ? PsiTreeUtil.getParentOfType(element, PsiClass.class) : method.getContainingClass();
      if (aClass instanceof PsiAnonymousClass) {
        element = aClass;
      }
      else {
        break;
      }
    }
    String location = method != null ? PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS | PsiFormatUtil.SHOW_CONTAINING_CLASS, PsiFormatUtil.SHOW_TYPE, 2)
                    : aClass != null ? PsiFormatUtil.formatClass(aClass, PsiFormatUtil.SHOW_NAME)
                    : null;
    if (location != null) {
      append(" in " + location, SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }
}

