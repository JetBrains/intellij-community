/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.uiDesigner;

import com.intellij.psi.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 *         Date: Jul 5, 2005
 */
final class FieldFormReference extends ReferenceInForm {
  private final PsiReference myClassReference;
  private final String myComponentClassName;
  private final TextRange myComponentClassNameRange;

  public FieldFormReference(final PsiPlainTextFile file, final PsiReference aClass, final TextRange fieldNameRange, @Nullable String componentClassName, @Nullable TextRange componentClassNameRange) {
    super(file, fieldNameRange);
    myClassReference = aClass;
    myComponentClassName = componentClassName;
    myComponentClassNameRange = componentClassNameRange;
  }

  public PsiElement resolve() {
    final PsiElement element = myClassReference.resolve();
    if(element instanceof PsiClass){
      return ((PsiClass)element).findFieldByName(getRangeText(), true);
    }
    return null;
  }

  protected @Nullable String getComponentClassName() {
    return myComponentClassName;
  }

  protected @Nullable TextRange getComponentClassNameTextRange() {
    return myComponentClassNameRange;
  }

  public PsiElement bindToElement(final PsiElement element) throws IncorrectOperationException {
    if (!(element instanceof PsiField)) {
      throw new IncorrectOperationException();
    }

    final PsiField field = (PsiField)element;
    if (!myClassReference.equals(field.getContainingClass())) {
      throw new IncorrectOperationException();
    }
    final String text = field.getName();
    updateRangeText(text);

    return myFile;
  }
}
