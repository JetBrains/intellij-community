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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Alexey.Ivanov
 */
public class PySuperArgumentsInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.wrong.super.arguments");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  private static class Visitor extends PyInspectionVisitor {

    public Visitor(final ProblemsHolder holder, LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyCallExpression(PyCallExpression node) {
      final PyExpression callee = node.getCallee();
      if (callee != null) {
        if (PyNames.SUPER.equals(callee.getName())) {
          PyExpression[] arguments = node.getArguments();
          if (arguments.length == 2) {
            if (arguments[0] instanceof PyReferenceExpression && arguments[1] instanceof PyReferenceExpression) {
              PyClass firstClass = findClassOf(arguments[0]);
              PyClass secondClass = findClassOf(arguments[1]);
              if (firstClass != null && secondClass != null) {
                if (!secondClass.isSubclass(firstClass, null)) {
                  registerProblem(
                    node.getArgumentList(),
                    PyBundle.message("INSP.$0.is.not.superclass.of.$1",
                    secondClass.getName(), firstClass.getName())
                  );
                }
              }
            }
          }
        }
      }
    }

    @Nullable
    private PyClass findClassOf(PyExpression argument) {
      PsiElement firstElement = ((PyReferenceExpression)argument).followAssignmentsChain(getResolveContext()).getElement();
      PyClass firstClass = null;
      if (firstElement instanceof PyClass) firstClass = (PyClass)firstElement;
      else if (firstElement instanceof PyExpression) {
        PyType first_type = myTypeEvalContext.getType((PyExpression)firstElement);
        if (first_type instanceof PyClassType) {
          firstClass = ((PyClassType)first_type).getPyClass();
        }
      }
      return firstClass;
    }
  }
}
