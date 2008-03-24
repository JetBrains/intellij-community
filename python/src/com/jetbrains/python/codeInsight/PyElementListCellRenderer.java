package com.jetbrains.python.codeInsight;

import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

/**
 * @author yole
 */
public class PyElementListCellRenderer extends PsiElementListCellRenderer {
  private static final PyLocationRenderer myLocationRenderer = new PyLocationRenderer();

  public String getElementText(final PsiElement element) {
    final String name = ((PsiNamedElement)element).getName();
    return name == null ? "" : name;
  }

  protected String getContainerText(final PsiElement element, final String name) {
    return null;
  }

  protected int getIconFlags() {
    return 0;
  }

  protected DefaultListCellRenderer getRightCellRenderer() {
    return myLocationRenderer;
  }

  private static class PyLocationRenderer extends DefaultListCellRenderer {
    private String myText;

    public String getText() {
      return myText;
    }

    public Component getListCellRendererComponent(final JList list,
                                                  final Object value,
                                                  final int index,
                                                  final boolean isSelected,
                                                  final boolean cellHasFocus) {
      final Component listCellRendererComponent = super.getListCellRendererComponent(list, value, index, isSelected,
                                                                                     cellHasFocus);
      customizeCellRenderer(value, isSelected);
      return listCellRendererComponent;
    }

    private void customizeCellRenderer(final Object value, final boolean selected) {
      PsiElement element = (PsiElement) value;
      if (element.isValid()) {
        myText = element.getContainingFile().getName();
      }
      else {
        myText = "";
      }
      setText(myText);
      setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 2));
      setHorizontalTextPosition(SwingConstants.LEFT);
      setBackground(selected ? UIUtil.getListSelectionBackground() : UIUtil.getListBackground());
      setForeground(selected ? UIUtil.getListSelectionForeground() : UIUtil.getInactiveTextColor());
    }
  }
}
