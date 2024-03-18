// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.binding;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  @Override
  public PsiElement resolve() {
    final PsiElement element = myClassReference.resolve();
    if(element instanceof PsiClass){
      return ((PsiClass)element).findFieldByName(getRangeText(), true);
    }
    return null;
  }

  public @Nullable String getComponentClassName() {
    return myComponentClassName;
  }

  public @Nullable TextRange getComponentClassNameTextRange() {
    return myComponentClassNameRange;
  }

  public boolean isCustomCreate() {
    return myCustomCreate;
  }

  @Override
  public PsiElement bindToElement(final @NotNull PsiElement element) throws IncorrectOperationException {
    if (!(element instanceof PsiField field)) {
      throw new IncorrectOperationException();
    }

    PsiClass fieldClass = field.getContainingClass();
    if (fieldClass == null || !myClassReference.isReferenceTo(fieldClass)) {
      throw new IncorrectOperationException();
    }
    final String text = field.getName();
    updateRangeText(text);

    return myFile;
  }
}
