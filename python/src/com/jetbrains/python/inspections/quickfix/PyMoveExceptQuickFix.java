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
package com.jetbrains.python.inspections.quickfix;

import com.google.common.collect.Lists;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class PyMoveExceptQuickFix implements LocalQuickFix {

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.NAME.move.except.up");
  }

  @NonNls
  @NotNull
  public String getFamilyName() {
    return getName();
  }

  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    final PyExceptPart part = PsiTreeUtil.getParentOfType(element, PyExceptPart.class);
    if (part == null) return;
    final PyExpression exceptClassExpression = part.getExceptClass();
    if (exceptClassExpression == null) return;

    final PsiElement exceptClass = ((PyReferenceExpression)exceptClassExpression).followAssignmentsChain(PyResolveContext.noImplicits()).getElement();
    if (exceptClass instanceof PyClass) {
      final PyTryExceptStatement statement = PsiTreeUtil.getParentOfType(part, PyTryExceptStatement.class);
      if (statement == null) return;

      PyExceptPart prevExceptPart = PsiTreeUtil.getPrevSiblingOfType(part, PyExceptPart.class);
      final ArrayList<PyClass> superClasses = Lists.newArrayList(((PyClass)exceptClass).getSuperClasses(null));
      while (prevExceptPart != null) {
        final PyExpression classExpression = prevExceptPart.getExceptClass();
        if (classExpression == null) return;
        final PsiElement aClass = ((PyReferenceExpression)classExpression).followAssignmentsChain(PyResolveContext.noImplicits()).getElement();
        if (aClass instanceof PyClass) {
          if (superClasses.contains(aClass)) {
            statement.addBefore(part, prevExceptPart);
            part.delete();
            return;
          }
        }
        prevExceptPart = PsiTreeUtil.getPrevSiblingOfType(prevExceptPart, PyExceptPart.class);
      }
    }
  }
}
