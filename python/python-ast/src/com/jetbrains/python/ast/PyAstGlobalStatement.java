// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;


@ApiStatus.Experimental
public interface PyAstGlobalStatement extends PyAstStatement, PyAstNamedElementContainer {
  PyAstTargetExpression @NotNull [] getGlobals();

  @Override
  @NotNull
  default List<PsiNamedElement> getNamedElements() {
    return Arrays.asList(getGlobals());
  }
}
