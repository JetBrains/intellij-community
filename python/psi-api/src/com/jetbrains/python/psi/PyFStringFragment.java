// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface PyFStringFragment extends PsiElement {
  @Nullable
  PyExpression getMainExpression();

  @NotNull
  List<PyFStringFragment> getFormatFragments();

  @Nullable
  PsiElement getColon();

  @Nullable
  PsiElement getClosingBrace();
}
