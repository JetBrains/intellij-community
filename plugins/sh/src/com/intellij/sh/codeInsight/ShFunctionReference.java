// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.codeInsight;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.sh.psi.ShFunctionDefinition;
import com.intellij.sh.psi.ShFunctionName;
import com.intellij.sh.psi.ShGenericCommandDirective;
import com.intellij.util.containers.hash.LinkedHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class ShFunctionReference extends PsiReferenceBase<PsiElement> {
  private final PsiFile myFile;
  private Map<String, FunctionContext> myExecutionContext;

  public ShFunctionReference(@NotNull PsiElement element) {
    super(element, TextRange.create(0, element.getTextLength()));
    myFile = myElement.getContainingFile();
  }

  @Nullable
  @Override
  public PsiElement resolve() {
    if (myFile == null) return null;
    myExecutionContext = new LinkedHashMap<>();

    PsiElement target = checkChildren(myFile);
    if (target != null) return target;
    return checkUnvisitedMethods();
  }

  private PsiElement checkChildren(PsiElement element) {
    if (element == null) return null;
    for (PsiElement child : element.getChildren()) {
      PsiElement target = collectStatistic(skipUnnecessaryNodes(child));
      if (target != null) return target;
    }
    return null;
  }

  private static PsiElement skipUnnecessaryNodes(@NotNull PsiElement element) {
    PsiElement nextElement = element;
    while (shouldBeSkipped(nextElement)) {
      nextElement = nextElement.getChildren()[0];
    }
    return nextElement;
  }

  private static boolean shouldBeSkipped(@NotNull PsiElement element) {
    return element.getChildren().length == 1 && !(element instanceof ShGenericCommandDirective || element instanceof ShFunctionDefinition);
  }

  private PsiElement collectStatistic(PsiElement element) {
    if (element == null) return null;
    if (element instanceof ShFunctionDefinition) {
      // If we meet function definition, should add it to the context like a not visited node
      handleFunction((ShFunctionDefinition)element);
      return null;
    }
    if (element instanceof ShGenericCommandDirective) {
      // Check if command linked to function which we searching for. If linked function is not visited, we should check
      // all its entire function and add them to the execution context
      if (myExecutionContext.containsKey(element.getText())) {
        FunctionContext functionContext = myExecutionContext.get(element.getText());
        if (element == myElement) return functionContext.function.getFunctionName();
        if (!functionContext.visited) {
          functionContext.visited = true;
          PsiElement target = checkChildren(functionContext.function);
          if (target != null) return target;
        }
      }
    }
    return checkChildren(element);
  }

  private void handleFunction(@NotNull ShFunctionDefinition functionDefinition) {
    ShFunctionName functionName = functionDefinition.getFunctionName();
    assert functionName != null;
    myExecutionContext.put(functionName.getText(), new FunctionContext(functionDefinition));
  }

  @Nullable
  private PsiElement checkUnvisitedMethods() {
    while (containsUnvisitedMethods()) {
      for (FunctionContext functionContext : myExecutionContext.values()) {
        if (!functionContext.visited) {
          functionContext.visited = true;
          PsiElement target = checkChildren(functionContext.function);
          if (target != null) return target;
        }
      }
    }
    return null;
  }

  private boolean containsUnvisitedMethods() {
    return !myExecutionContext.values().stream().allMatch(it -> it.visited);
  }

  private static class FunctionContext {
    private final ShFunctionDefinition function;
    private boolean visited;

    private FunctionContext(ShFunctionDefinition function) {
      this.function = function;
    }
  }
}
