/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 18.06.2002
 * Time: 13:47:11
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.ui;

import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiClass;
import com.intellij.refactoring.RefactoringBundle;

import javax.swing.*;
import java.awt.*;

/**
 * Renders a list cell which contains a class
 */
public class ClassCellRenderer extends DefaultListCellRenderer {
  private final boolean myShowReadOnly;
  public ClassCellRenderer() {
    setOpaque(true);
    myShowReadOnly = true;
  }

  public ClassCellRenderer(boolean showReadOnly) {
    setOpaque(true);
    myShowReadOnly = showReadOnly;
  }

  public Component getListCellRendererComponent(
          JList list,
          Object value,
          int index,
          boolean isSelected,
          boolean cellHasFocus) {
    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

    PsiClass aClass = (PsiClass) value;
    setText(getClassText(aClass));

    final int flags;
    if (myShowReadOnly) {
      flags = Iconable.ICON_FLAG_READ_STATUS | Iconable.ICON_FLAG_VISIBILITY;
    } else {
      flags = Iconable.ICON_FLAG_VISIBILITY;
    }
    Icon icon = aClass.getIcon(flags);
    if(icon != null) setIcon(icon);
    return this;
  }

  static String getClassText(PsiClass aClass) {
    String qualifiedName = aClass.getQualifiedName();
    String text;
    if (qualifiedName != null) {
      text = qualifiedName;
    } else {
      String name = aClass.getName();
      if (name != null) {
        text = name;
      } else {
        text = RefactoringBundle.message("anonymous.class.text");
      }
    }
    return text;
  }
}
