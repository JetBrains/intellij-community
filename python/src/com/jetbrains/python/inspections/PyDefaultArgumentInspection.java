/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.inspections.quickfix.PyDefaultArgumentQuickFix;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Alexey.Ivanov
 */
public class PyDefaultArgumentInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.default.argument");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  private static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyNamedParameter(PyNamedParameter node) {
      PyExpression defaultValue = node.getDefaultValue();
      if (defaultValue != null) {
        if (defaultValue instanceof PyListLiteralExpression || defaultValue instanceof PyDictLiteralExpression) {
          registerProblem(defaultValue, "Default argument value is mutable", new PyDefaultArgumentQuickFix());
        }
        if (defaultValue instanceof PyCallExpression) {
          PyExpression callee = ((PyCallExpression)defaultValue).getCallee();
          if (callee != null && "dict".equals(callee.getText()))
            registerProblem(defaultValue, "Default argument value is mutable", new PyDefaultArgumentQuickFix());
        }
      }
    }
  }
}
