// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.search;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.pyi.PyiUtil;
import org.jetbrains.annotations.NotNull;


public final class PyDefinitionsSearch implements QueryExecutor<PsiElement, PsiElement> {
  @Override
  public boolean execute(@NotNull PsiElement element, @NotNull Processor<? super PsiElement> consumer) {
    if (element instanceof PyElement) {
      PsiElement finalElement = element;
      boolean isInsideStub = ReadAction.compute(() -> PyiUtil.isInsideStub(finalElement));
      if (isInsideStub) {
        var originalElement = ReadAction.compute(() -> PyiUtil.getOriginalElement((PyElement)finalElement));
        if (originalElement != null) {
          element = originalElement;
          if (!consumer.process(element)) return false;
        }
      }
    }
    if (element instanceof PyClass) {
      return processInheritors((PyClass)element, consumer);
    }
    else if (element instanceof PyFunction) {
      return processOverridingMethods((PyFunction)element, consumer);
    }
    else if (element instanceof PyTargetExpression) {
      return processAssignmentStatement(element, consumer);
    }
    return true;
  }

  private static boolean processInheritors(@NotNull PyClass pyClass, @NotNull Processor<? super PsiElement> consumer) {
    return ReadAction.compute(() -> PyClassInheritorsSearch.search(pyClass, true)).forEach(consumer);
  }

  private static boolean processOverridingMethods(@NotNull PyFunction pyFunction,
                                                  @NotNull Processor<? super PsiElement> consumer) {
    return ReadAction.compute(() -> PyOverridingMethodsSearch.search(pyFunction, true)).forEach(consumer);
  }

  private static boolean processAssignmentStatement(@NotNull PsiElement element,
                                                    @NotNull Processor<? super PsiElement> consumer) {
    PsiElement parent = ReadAction.compute(() -> element.getParent());
    return !(parent instanceof PyAssignmentStatement) || consumer.process(parent);
  }
}
