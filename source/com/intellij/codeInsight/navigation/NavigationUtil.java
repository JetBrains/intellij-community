package com.intellij.codeInsight.navigation;

import com.intellij.ide.util.EditSourceUtil;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.ide.util.gotoByName.GotoSymbolCellRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.ui.ListPopup;

import javax.swing.*;

/**
 * @author ven
 */
public final class NavigationUtil {
  public static ListPopup getPsiElementPopup(PsiElement[] elements, String title, final Project project) {
    PsiElementListCellRenderer renderer = new GotoSymbolCellRenderer();
    return getPsiElementPopup(elements, renderer, title, project);

  }

  public static ListPopup getPsiElementPopup(final PsiElement[] elements,
                                             final PsiElementListCellRenderer renderer,
                                             final String title,
                                             final Project project) {
    final JList list = new JList(elements);
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setCellRenderer(renderer);
    renderer.installSpeedSearch(list);

    final Runnable runnable = new Runnable() {
      public void run() {
        int index = list.getSelectedIndex();
        if (index < 0) return;
        PsiElement element = (PsiElement) list.getSelectedValue();
        Navigatable descriptor = EditSourceUtil.getDescriptor(element);
        if (descriptor != null) {
          descriptor.navigate(true);
        }
      }
    };

    ListPopup listPopup = new ListPopup(title, list, runnable, project);
    return listPopup;
  }
}
