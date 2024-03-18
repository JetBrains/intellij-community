// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.liveTemplates;

import com.intellij.codeInsight.template.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public final class PyFunctionNameMacro extends Macro {
  @Override
  public String getName() {
    return "pyFunctionName";
  }

  @Nullable
  @Override
  public Result calculateResult(Expression @NotNull [] params, ExpressionContext context) {
    PsiElement place = context.getPsiElementAtStartOffset();
    PyFunction pyFunction = PsiTreeUtil.getParentOfType(place, PyFunction.class);
    if (pyFunction == null) {
      return null;
    }
    String name = pyFunction.getName();
    return name == null ? null : new TextResult(name);
  }

  @Override
  public boolean isAcceptableInContext(TemplateContextType context) {
    return context instanceof PythonTemplateContextType;
  }
}
