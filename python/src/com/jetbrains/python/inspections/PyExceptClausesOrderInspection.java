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
import com.intellij.util.containers.HashSet;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.inspections.quickfix.PyMoveExceptQuickFix;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author Alexey.Ivanov
 */
public class PyExceptClausesOrderInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.bad.except.clauses.order");
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
    public void visitPyTryExceptStatement(PyTryExceptStatement node) {
      PyExceptPart[] exceptParts = node.getExceptParts();
      if (exceptParts.length > 1) {
        Set<PyClass> exceptClasses = new HashSet<>();
        for (PyExceptPart exceptPart : exceptParts) {
          PyExpression exceptClass = exceptPart.getExceptClass();
          if (exceptClass instanceof PyReferenceExpression) {
            PsiElement element = ((PyReferenceExpression) exceptClass).followAssignmentsChain(getResolveContext()).getElement();
            if (element instanceof PyClass) {
              PyClass pyClass = (PyClass)element;
              if (exceptClasses.contains(pyClass)) {
                registerProblem(exceptClass, PyBundle.message("INSP.class.$0.already.caught", pyClass.getName()));
              } else {
                for (PyClass superClass: pyClass.getSuperClasses(null)) {
                  if (exceptClasses.contains(superClass)) {
                    registerProblem(exceptClass, PyBundle.message("INSP.class.$0.superclass.$1.already.caught", superClass.getName(), pyClass.getName()),
                                    new PyMoveExceptQuickFix());
                  }
                }
              }
              exceptClasses.add(pyClass);
            }
          }
        }
      }
    }
  }
}
