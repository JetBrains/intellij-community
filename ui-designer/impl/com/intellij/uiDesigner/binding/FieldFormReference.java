/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.uiDesigner.binding;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 *         Date: Jul 5, 2005
 */
public final class FieldFormReference extends ReferenceInForm {
  private final PsiReference myClassReference;
  private final String myComponentClassName;
  private final TextRange myComponentClassNameRange;
  private final boolean myCustomCreate;

  public FieldFormReference(final PsiPlainTextFile file,
                            final PsiReference aClass,
                            final TextRange fieldNameRange,
                            @Nullable String componentClassName,
                            @Nullable TextRange componentClassNameRange,
                            final boolean customCreate) {
    super(file, fieldNameRange);
    myClassReference = aClass;
    myComponentClassName = componentClassName;
    myComponentClassNameRange = componentClassNameRange;
    myCustomCreate = customCreate;
  }

  public PsiElement resolve() {
    final PsiElement element = myClassReference.resolve();
    if(element instanceof PsiClass){
      return ((PsiClass)element).findFieldByName(getRangeText(), true);
    }
    return null;
  }

  @Nullable
  public String getComponentClassName() {
    return myComponentClassName;
  }

  @Nullable
  public TextRange getComponentClassNameTextRange() {
    return myComponentClassNameRange;
  }

  public boolean isCustomCreate() {
    return myCustomCreate;
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
