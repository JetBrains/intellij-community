// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.StringLiteralExpression;
import org.jetbrains.annotations.NotNull;

/**
 * Some reference for string literal (handles rename and range correctly)
 *
 * @author Ilya.Kazakevich
 */
public abstract class PyStringLiteralReference extends BaseReference {
  protected final @NotNull StringLiteralExpression myStringLiteral;

  protected PyStringLiteralReference(final @NotNull StringLiteralExpression element) {
    super(element);
    myStringLiteral = element;
  }

  // 1 instead of 1 in range and "-1" at the end because we do not need quotes
  @Override
  public final @NotNull TextRange getRangeInElement() {
    return myStringLiteral.getStringValueTextRange();
  }

  @Override
  public PsiElement handleElementRename(final @NotNull String newElementName) {
    final PsiElement newString = PyElementGenerator.getInstance(myElement.getProject()).createStringLiteral(myStringLiteral, newElementName);
    myStringLiteral.replace(newString);
    return newString;
  }
}
