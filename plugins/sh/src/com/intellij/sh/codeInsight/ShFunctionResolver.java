// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.codeInsight;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.sh.psi.ShFunctionDefinition;
import com.intellij.sh.psi.ShFunctionName;
import com.intellij.sh.psi.ShGenericCommandDirective;
import com.intellij.sh.psi.ShLiteral;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.LinkedHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

class ShFunctionResolver implements ResolveCache.Resolver {
  private final Map<String, FunctionContext> myExecutionContext;
  private final PsiElement myElement;
  private final PsiFile myFile;

  ShFunctionResolver(@NotNull PsiFile file, @NotNull PsiElement element) {
    myFile = file;
    myElement = element;
    myExecutionContext = new LinkedHashMap<>();
  }

  @Override
  @Nullable
  public PsiElement resolve(@NotNull PsiReference ref, boolean incompleteCode) {
    // Execution context should be build, not check inner function if it was not called
    PsiElement target = checkChildren(myFile, false);
    if (target != null) return target;
    return checkUnvisitedMethods();
  }

  @Nullable
  private PsiElement checkUnvisitedMethods() {
    List<FunctionContext> notVisitedFunctions = ContainerUtil.filter(myExecutionContext.values(), it -> !it.visited);
    // In this case we should visit all not visited function one by one with checking inner function also
    for (FunctionContext functionContext : notVisitedFunctions) {
      functionContext.visited = true;
      PsiElement target = checkChildren(functionContext.function, true);
      if (target != null) return target;
    }
    return null;
  }

  private PsiElement checkChildren(PsiElement element, boolean visitInnerFunctions) {
    if (element == null) return null;
    for (PsiElement child : element.getChildren()) {
      PsiElement target = buildExecutionContext(skipUnnecessaryNodes(child), visitInnerFunctions);
      if (target != null) return target;
    }
    return null;
  }

  private PsiElement buildExecutionContext(PsiElement element, boolean visitInnerFunctions) {
    if (element == null) return null;
    if (element instanceof ShFunctionDefinition) {
      // If we meet function definition, should add it to the context like a not visited node
      return handleFunction((ShFunctionDefinition)element, visitInnerFunctions);
    }
    if (element instanceof ShGenericCommandDirective) {
      // Check if command linked to function which we searching for. If linked function is not visited, we should check
      // all its entire function and add them to the execution context
      ShGenericCommandDirective genericCommand = (ShGenericCommandDirective)element;
      ShLiteral literal = genericCommand.getLiteral();
      if (literal != null && myExecutionContext.containsKey(literal.getText())) {
        FunctionContext functionContext = myExecutionContext.get(literal.getText());
        if (visitInnerFunctions) {
          if (literal == myElement && functionContext.visited) return functionContext.function.getFunctionName();
        } else {
          if (literal == myElement) return functionContext.function.getFunctionName();
          if (!functionContext.visited) {
            functionContext.visited = true;
            PsiElement target = checkChildren(functionContext.function, visitInnerFunctions);
            if (target != null) return target;
          }
        }
      }
    }
    return checkChildren(element, visitInnerFunctions);
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

  private PsiElement handleFunction(@NotNull ShFunctionDefinition functionDefinition, boolean visitInnerFunctions) {
    ShFunctionName functionName = functionDefinition.getFunctionName();
    assert functionName != null;
    FunctionContext functionContext = new FunctionContext(functionDefinition);
    myExecutionContext.put(functionName.getText(), functionContext);
    if (!visitInnerFunctions) return null;
    functionContext.visited = true;
    return checkChildren(functionDefinition, visitInnerFunctions);
  }

  private static class FunctionContext {
    private final ShFunctionDefinition function;
    private boolean visited;

    private FunctionContext(ShFunctionDefinition function) {
      this.function = function;
    }
  }
}
