/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml.impl;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import com.intellij.util.Function;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.ui.*;

import javax.swing.table.TableCellEditor;

/**
 * @author peter
 */
public class JavaDomApplicationComponent {
  public JavaDomApplicationComponent(DomUIFactory factory) {
    factory.registerCustomControl(PsiClass.class, new Function<DomWrapper<String>, BaseControl>() {
      public BaseControl fun(final DomWrapper<String> wrapper) {
        return new PsiClassControl(wrapper, false);
      }
    });
    factory.registerCustomControl(PsiType.class, new Function<DomWrapper<String>, BaseControl>() {
      public BaseControl fun(final DomWrapper<String> wrapper) {
        return new PsiTypeControl(wrapper, false);
      }
    });

    factory.registerCustomCellEditor(PsiClass.class, new Function<DomElement, TableCellEditor>() {
      public TableCellEditor fun(final DomElement element) {
        return new PsiClassTableCellEditor(element.getManager().getProject(), element.getResolveScope());
      }
    });
  }

}