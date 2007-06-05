/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.psi.impl.beanProperties;

import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * @author Dmitry Avdeev
 */
public class BeanPropertyFindUsagesHandler extends FindUsagesHandler {

  private final BeanProperty myProperty;

  public BeanPropertyFindUsagesHandler(final BeanProperty property) {
    super(property.getPsiElement());
    myProperty = property;
  }


  @NotNull
  public PsiElement[] getPrimaryElements() {
    final ArrayList<PsiElement> elements = new ArrayList<PsiElement>(2);
    final PsiMethod getter = myProperty.getGetter();
    if (getter != null) {
      elements.add(getter);
    }
    final PsiMethod setter = myProperty.getSetter();
    if (setter != null) {
      elements.add(setter);
    }
    return elements.toArray(PsiElement.EMPTY_ARRAY);
  }
}
