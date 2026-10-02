// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.findUsages;

import com.jetbrains.python.psi.PyTargetExpression;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Important note: please update PyFindUsagesHandlerFactory#proxy on any changes here.
 */
@ApiStatus.Internal
public class PyTargetExpressionFindUsagesHandler extends PyFindUsagesHandler {
  public PyTargetExpressionFindUsagesHandler(@NotNull PyTargetExpression psiElement) {
    super(psiElement);
  }
}
