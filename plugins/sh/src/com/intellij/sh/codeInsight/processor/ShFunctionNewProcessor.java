// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.codeInsight.processor;

import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.sh.psi.ShFunctionDefinition;
import com.intellij.sh.psi.ShFunctionName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShFunctionNewProcessor implements PsiScopeProcessor {
  private final String myFunctionName;
  private ShFunctionName myResult;

  public ShFunctionNewProcessor(String name) {
    myFunctionName = name;
  }

  @Override
  public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
    if (element instanceof ShFunctionDefinition) {
      ShFunctionDefinition functionDefinition = (ShFunctionDefinition)element;
      ShFunctionName functionName = functionDefinition.getFunctionName();
      assert functionName != null : "Function name can't ne null";
      if (!functionName.getText().equals(myFunctionName)) return true;
      myResult = functionName;
      return false;
    }
    return true;
  }

  @Nullable
  public ShFunctionName getFunction() {
    return myResult;
  }
}