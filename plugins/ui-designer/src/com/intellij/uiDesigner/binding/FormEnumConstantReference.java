// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.uiDesigner.binding;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPlainTextFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FormEnumConstantReference extends ReferenceInForm {
  private final PsiClassType myEnumClass;

  protected FormEnumConstantReference(final PsiPlainTextFile file, final TextRange range, final PsiClassType enumClass) {
    super(file, range);
    myEnumClass = enumClass;
  }

  @Override
  public @Nullable PsiElement resolve() {
    PsiClass enumClass = myEnumClass.resolve();
    if (enumClass == null) return null;
    return enumClass.findFieldByName(getRangeText(), false);
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }
}
