// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.search;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import com.intellij.util.QueryExecutor;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.pyi.PyiFile;
import com.jetbrains.python.pyi.PyiUtil;
import org.jetbrains.annotations.NotNull;


public class PyDefinitionsSearch implements QueryExecutor<PsiElement, PsiElement> {
  @Override
  public boolean execute(@NotNull final PsiElement e, @NotNull final Processor<? super PsiElement> consumer) {
    if (e instanceof PyClass) {
      final Query<PyClass> query = PyClassInheritorsSearch.search((PyClass)e, true);
      return query.forEach(consumer);
    }
    else if (e instanceof PyFunction) {
      final Query<PyFunction> query =
        ReadAction.compute(() -> PyOverridingMethodsSearch.search((PyFunction)e, true));

      return query.forEach(consumer);
    }
    else if (e instanceof PyTargetExpression) {  // PY-237
      final PsiElement parent = ReadAction.compute(() -> e.getParent());

      if (parent instanceof PyAssignmentStatement) {
        return consumer.process(parent);
      }
    }
    else if (e instanceof PyiFile) {
      final PsiElement originalElement = ReadAction.compute(() -> PyiUtil.getOriginalElement((PyiFile)e));
      if (originalElement != null) {
        consumer.process(originalElement);
      }
    }
    return true;
  }
}
