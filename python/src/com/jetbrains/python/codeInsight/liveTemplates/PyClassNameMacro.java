// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.liveTemplates;

import com.intellij.codeInsight.template.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public final class PyClassNameMacro extends Macro {
  @Override
  public String getName() {
    return "pyClassName";
  }

  @Override
  public @Nullable Result calculateResult(Expression @NotNull [] params, ExpressionContext context) {
    PsiElement place = context.getPsiElementAtStartOffset();
    PyClass pyClass = PsiTreeUtil.getParentOfType(place, PyClass.class);
    if (pyClass == null) {
      return null;
    }
    String name = pyClass.getName();
    return name == null ? null : new TextResult(name);
  }

  @Override
  public boolean isAcceptableInContext(TemplateContextType context) {
    return context instanceof PythonTemplateContextType;
  }
}
