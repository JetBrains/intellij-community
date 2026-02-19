// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.jetbrains.python.psi.PyExpressionStatement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public interface PyStatementEffectQuickFixProvider {
  ExtensionPointName<PyStatementEffectQuickFixProvider> EP_NAME = ExtensionPointName.create("Pythonid.statementEffectQuickFixProvider");


  @Nullable
  LocalQuickFix getQuickFix(@NotNull PyExpressionStatement expressionStatement);
}