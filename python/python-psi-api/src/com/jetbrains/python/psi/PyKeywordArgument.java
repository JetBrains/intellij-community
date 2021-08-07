// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import com.intellij.lang.ASTNode;


public interface PyKeywordArgument extends PyExpression, PsiNamedElement {
  @NonNls
  @Nullable
  String getKeyword();

  @Nullable
  PyExpression getValueExpression();

  @Nullable
  ASTNode getKeywordNode();
}
