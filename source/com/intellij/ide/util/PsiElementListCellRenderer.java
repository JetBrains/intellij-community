package com.intellij.ide.util;

import com.intellij.ide.IconUtilEx;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;
import java.awt.*;
import java.util.Comparator;

public abstract class PsiElementListCellRenderer<T extends PsiElement> extends JPanel implements ListCellRenderer {
  protected PsiElementListCellRenderer() {
    super(new BorderLayout());
  }

  private class LeftRenderer extends ColoredListCellRenderer {
    protected void customizeCellRenderer(
      JList list,
      Object value,
      int index,
      boolean selected,
      boolean hasFocus
      ) {
      if (value instanceof PsiElement) {
        PsiElement element = (PsiElement)value;
        String name = getElementText((T)element);
        Color color = list.getForeground();
        PsiFile psiFile = element.getContainingFile();
        if (psiFile != null) {
          VirtualFile vFile = psiFile.getVirtualFile();
          if (vFile != null) {
            FileStatus status = FileStatusManager.getInstance(psiFile.getProject()).getStatus(vFile);
            color = status.getColor();
          }
        }
        append(name, new SimpleTextAttributes(Font.PLAIN, color));
        setIcon(element.getIcon(getIconFlags()));

        String containerText = getContainerText(element);
        if (containerText != null) {
          append(" " + containerText, new SimpleTextAttributes(Font.PLAIN, Color.GRAY));
        }
      }
      else {
        setIcon(IconUtilEx.getEmptyIcon(false));
        append(value == null ? "" : value.toString(), new SimpleTextAttributes(Font.PLAIN, list.getForeground()));
      }
      setPaintFocusBorder(false);
      setBackground(UIManager.getColor(selected ? "List.selectionBackground" : "List.background"));
    }
  }

  public Component getListCellRendererComponent(JList list,
                                                Object value,
                                                int index,
                                                boolean isSelected,
                                                boolean cellHasFocus) {
    removeAll();
    final Component leftCellRendererComponent =
      new LeftRenderer().getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
    add(leftCellRendererComponent, BorderLayout.WEST);
    if (UISettings.getInstance().SHOW_ICONS_IN_QUICK_NAVIGATION) {
      final Component rightCellRendererComponent =
        new PsiElementModuleRenderer().getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      add(rightCellRendererComponent, BorderLayout.EAST);
      final JPanel spacer = new JPanel();
      final Dimension size = rightCellRendererComponent.getSize();
      spacer.setSize(new Dimension((int)(size.width * 0.015 + leftCellRendererComponent.getSize().width * 0.015), size.height));
      spacer.setBackground(UIManager.getColor(isSelected ? "List.selectionBackground" : "List.background"));
      add(spacer, BorderLayout.CENTER);
    }
    setBackground(UIManager.getColor(isSelected ? "List.selectionBackground" : "List.background"));
    return this;
  }

  public abstract String getElementText(T element);

  protected abstract String getContainerText(PsiElement element);

  protected abstract int getIconFlags();

  protected Icon getIcon(PsiElement element) {
    return element.getIcon(getIconFlags());
  }

  public Comparator<T> getComparator() {
    return new Comparator<T>() {
      public int compare(T o1, T o2) {
        return getText(o1).compareTo(getText(o2));
      }

      private String getText(T element) {
        String elementText = getElementText(element);
        String containerText = getContainerText(element);
        return containerText != null ? elementText + " " + containerText : elementText;
      }
    };
  }

  public void installSpeedSearch(JList list) {
    new ListSpeedSearch(list) {
      protected String getElementText(Object o) {
        if (o instanceof PsiElement) {
          return PsiElementListCellRenderer.this.getElementText((T)o);
        }
        else {
          return o.toString();
        }
      }
    };
  }
}
