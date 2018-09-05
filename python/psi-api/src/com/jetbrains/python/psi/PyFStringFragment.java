// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PyFStringFragment extends PyElement {
  @Nullable
  PyExpression getExpression();

  @NotNull
  TextRange getExpressionContentRange();
  
  @Nullable
  PsiElement getTypeConversion();

  @Nullable
  PyFStringFragmentFormatPart getFormatPart();
  
  @Nullable
  PsiElement getClosingBrace();
}
