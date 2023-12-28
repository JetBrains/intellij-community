// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.jetbrains.python.ast.PyAstElementKt.findChildByClass;

@ApiStatus.Experimental
public interface PyAstKeywordPattern extends PyAstPattern {
  default @NotNull String getKeyword() {
    return getKeywordElement().getText();
  }

  default @NotNull PsiElement getKeywordElement() {
    return getFirstChild();
  }

  default @Nullable PyAstPattern getValuePattern() {
    return findChildByClass(this, PyAstPattern.class);
  }

  @Override
  default boolean isIrrefutable() {
    return false;
  }
}
