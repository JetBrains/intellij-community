package com.intellij.ide.util;

import com.intellij.ide.IconUtilEx;
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

public abstract class PsiElementListCellRenderer extends ColoredListCellRenderer{
  protected void customizeCellRenderer(
    JList list,
    Object value,
    int index,
    boolean selected,
    boolean hasFocus
  ){
    if (value instanceof PsiElement){
      PsiElement element = (PsiElement)value;
      String name = getElementText(element);
      Color color = list.getForeground();
      PsiFile psiFile = element.getContainingFile();
      if (psiFile != null) {
        VirtualFile vFile = psiFile.getVirtualFile();
        if (vFile != null) {
          FileStatus status = FileStatusManager.getInstance(psiFile.getProject()).getStatus(vFile);
          color = status.getColor();
        }
      }
      append(name,new SimpleTextAttributes(Font.PLAIN, color));
      setIcon(element.getIcon(getIconFlags()));

      String containerText = getContainerText(element);
      if (containerText != null){
        append(" " + containerText, new SimpleTextAttributes(Font.PLAIN, Color.GRAY));
      }
    }
    else{
      setIcon(IconUtilEx.getEmptyIcon(false));
      append(value == null ? "" : value.toString(), new SimpleTextAttributes(Font.PLAIN, list.getForeground()));
    }
  }


  public abstract String getElementText(PsiElement element);

  protected abstract String getContainerText(PsiElement element);

  protected abstract int getIconFlags();

  protected Icon getIcon(PsiElement element){
    return element.getIcon(getIconFlags());
  }

  public Comparator<Object> getComparator(){
    return new Comparator<Object>() {
      public int compare(Object o1, Object o2) {
        return getText(o1).compareTo(getText(o2));
      }

      private String getText(Object o){
        if (o instanceof PsiElement){
          PsiElement element = (PsiElement)o;
          String elementText = getElementText(element);
          String containerText = getContainerText(element);
          return containerText != null ? elementText + " " + containerText : elementText;
        }
        else{
          return o.toString();
        }
      }
    };
  }

  public void installSpeedSearch(JList list){
    new ListSpeedSearch(list){
      protected String getElementText(Object o){
        if (o instanceof PsiElement){
          return PsiElementListCellRenderer.this.getElementText((PsiElement)o);
        }
        else{
          return o.toString();
        }
      }
    };
  }
}
