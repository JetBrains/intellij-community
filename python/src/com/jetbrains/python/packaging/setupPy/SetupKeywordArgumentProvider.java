/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.packaging.setupPy;

import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyKeywordArgumentProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class SetupKeywordArgumentProvider implements PyKeywordArgumentProvider {
  @NotNull
  @Override
  public List<String> getKeywordArguments(PyFunction function, PyCallExpression callExpr) {
    if ("setup".equals(function.getName())) {
      final ScopeOwner scopeOwner = PsiTreeUtil.getParentOfType(function, ScopeOwner.class, true);
      if (scopeOwner instanceof PyFile) {
        final PyFile file = (PyFile)scopeOwner;
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
