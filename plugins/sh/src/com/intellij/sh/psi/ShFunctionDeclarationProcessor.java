// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.psi;

import com.intellij.openapi.util.Conditions;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.sh.psi.impl.ShLazyBlockImpl;
import com.intellij.sh.psi.impl.ShLazyDoBlockImpl;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ShFunctionDeclarationProcessor implements PsiScopeProcessor {
  // Key in еру map is a parent of element(scope of function declaration e.g block for locally defined functions or file for global functions)
  private final Map<PsiElement, MultiMap<String, ShFunctionName>> functionsDeclarationsByScope = new HashMap<>();
  @Override
  public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
    if (element instanceof ShFunctionDefinition) {
      ShFunctionDefinition functionDefinition = (ShFunctionDefinition)element;
      PsiElement parent = PsiTreeUtil.findFirstParent(functionDefinition, Conditions.instanceOf(ShLazyDoBlockImpl.class,
                                                                                                ShLazyBlockImpl.class,
                                                                                                ShFile.class));
      assert parent != null : "Parent for element should be at least file";
      ShFunctionName functionName = functionDefinition.getFunctionName();
      assert functionName != null : "Function name can't ne null";
      functionsDeclarationsByScope.computeIfAbsent(parent, key -> MultiMap.create()).putValue(functionName.getText(), functionName);
    }
    return true;
  }

  @NotNull
  public Map<PsiElement, MultiMap<String, ShFunctionName>> getFunctionsDeclarationsWithScope() {
    return functionsDeclarationsByScope;
  }

  //@Override
  //public boolean shouldProcess(@NotNull FileElementTypes kind) {
  //  return kind == FileElementTypes.METHOD;
  //}
}

enum FileElementTypes {
  METHOD,
  VARIABLE
}