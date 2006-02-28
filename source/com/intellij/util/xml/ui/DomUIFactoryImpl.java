/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import com.intellij.psi.PsiClass;
import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.TableCellEditor;

/**
 * @author peter
 */
public class DomUIFactoryImpl extends DomUIFactory {
  protected TableCellEditor createCellEditor(DomElement element, Class type) {
    if (String.class.equals(type)) {
      return new DefaultCellEditor(removeBorder(new JTextField()));
    }

    if (PsiClass.class.equals(type)) {
      return new PsiClassTableCellEditor(element.getManager().getProject(), element.getResolveScope());
    }

    if (Enum.class.isAssignableFrom(type)) {
      return new DefaultCellEditor(removeBorder(EnumControl.createEnumComboBox(type)));
    }

    assert false : "Type not supported: " + type;
    return null;
  }

  private static <T extends JComponent> T removeBorder(final T component) {
    component.setBorder(new EmptyBorder(0, 0, 0, 0));
    return component;
  }

  @NonNls
  public String getComponentName() {
    return getClass().getName();
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}
