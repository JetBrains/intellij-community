// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.backend.codeInsight.processor;

import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.sh.psi.ShFunctionDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShFunctionDeclarationProcessor implements PsiScopeProcessor {
  private final String myFunctionName;
  private ShFunctionDefinition myResult;

  public ShFunctionDeclarationProcessor(@NotNull String name) {
    myFunctionName = name;
  }

  @Override
  public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
    if (!(element instanceof ShFunctionDefinition functionDefinition)) return true;
    PsiElement identifier = functionDefinition.getWord();
    if (identifier == null) return true;
    if (!identifier.getText().equals(myFunctionName)) return true;
    myResult = functionDefinition;
    return false;
  }

  public @Nullable ShFunctionDefinition getFunction() {
    return myResult;
  }
}