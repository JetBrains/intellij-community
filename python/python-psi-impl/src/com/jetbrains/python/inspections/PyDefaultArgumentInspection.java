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
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.inspections.quickfix.PyDefaultArgumentQuickFix;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Alexey.Ivanov
 */
public class PyDefaultArgumentInspection extends PyInspection {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  private static class Visitor extends PyInspectionVisitor {
    Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyNamedParameter(PyNamedParameter node) {
      final PyExpression defaultValue = node.getDefaultValue();
      if (PyUtil.isForbiddenMutableDefault(defaultValue, myTypeEvalContext)) {
        registerProblem(defaultValue, "Default argument value is mutable", new PyDefaultArgumentQuickFix());
      }
    }
  }
}
