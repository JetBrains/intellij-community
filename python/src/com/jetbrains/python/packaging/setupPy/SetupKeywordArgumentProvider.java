// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging.setupPy;

import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyKeywordArgumentProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;


public final class SetupKeywordArgumentProvider implements PyKeywordArgumentProvider {
  @NotNull
  @Override
  public List<String> getKeywordArguments(PyFunction function, PyCallExpression callExpr) {
    if ("setup".equals(function.getName())) {
      final ScopeOwner scopeOwner = PsiTreeUtil.getParentOfType(function, ScopeOwner.class, true);
      if (scopeOwner instanceof PyFile file) {
        if (file.getName().equals("core.py") && file.getParent().getName().equals("distutils")) {
          final List<String> arguments = getSetupPyKeywordArguments(file);
          if (arguments != null) {
            return arguments;
          }
        }
      }
    }
    return Collections.emptyList();
  }

  @Nullable
  private static List<String> getSetupPyKeywordArguments(PyFile file) {
    final PyTargetExpression keywords = file.findTopLevelAttribute("setup_keywords");
    if (keywords != null) {
      return PyUtil.strListValue(keywords.findAssignedValue());
    }
    return null;
  }
}
