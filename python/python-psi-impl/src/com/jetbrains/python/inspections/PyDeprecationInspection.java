/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.pyi.PyiFile;
import com.jetbrains.python.pyi.PyiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public final class PyDeprecationInspection extends PyInspection {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                                 final boolean isOnTheFly,
                                                 @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, PyInspectionVisitor.getContext(session));
  }

  private static class Visitor extends PyInspectionVisitor {
    Visitor(@Nullable ProblemsHolder holder,
            @NotNull TypeEvalContext context) {
      super(holder, context);
    }

    @Override
    public void visitPyBinaryExpression(@NotNull PyBinaryExpression node) {
      final PsiElement resolveResult = node.getReference(getResolveContext()).resolve();
      if (resolveResult instanceof PyDeprecatable) {
        @NlsSafe String deprecationMessage = ((PyDeprecatable)resolveResult).getDeprecationMessage();
        if (deprecationMessage != null) {
          registerProblem(node.getPsiOperator(), deprecationMessage, ProblemHighlightType.WARNING);
        }
      }
    }

    @Override
    public void visitPyReferenceExpression(@NotNull PyReferenceExpression node) {
      final PyExceptPart exceptPart = PsiTreeUtil.getParentOfType(node, PyExceptPart.class);
      if (exceptPart != null) {
        final PyExpression exceptClass = exceptPart.getExceptClass();
        if (exceptClass != null && "ImportError".equals(exceptClass.getText())) return;
      }
      final PsiElement resolveResult = resolve(node);
      final PyFromImportStatement importStatement = PsiTreeUtil.getParentOfType(node, PyFromImportStatement.class);
      if (importStatement != null) {
        final PsiElement element = importStatement.resolveImportSource();
        if (resolveResult != null && element != resolveResult.getContainingFile()) return;
      }
      @NlsSafe String deprecationMessage = null;
      if (resolveResult instanceof PyDeprecatable deprecatable) {
        deprecationMessage = deprecatable.getDeprecationMessage();

        if (deprecationMessage == null && !(resolveResult.getContainingFile() instanceof PyiFile)) {
          PsiElement stub = PyiUtil.getPythonStub((PyElement)deprecatable);
          if (stub instanceof PyDeprecatable stubDeprecatable) {
            deprecationMessage = stubDeprecatable.getDeprecationMessage();
          }
        }
      }
      else if (resolveResult instanceof PyFile) {
        deprecationMessage = ((PyFile)resolveResult).getDeprecationMessage();
      }
      if (deprecationMessage != null) {
        ASTNode nameElement = node.getNameElement();
        registerProblem(nameElement == null ? node : nameElement.getPsi(), deprecationMessage, ProblemHighlightType.LIKE_DEPRECATED);
      }
    }

    private @Nullable PyElement resolve(@NotNull PyReferenceExpression node) {
      final PyElement resolve = PyUtil.as(node.getReference(getResolveContext()).resolve(), PyElement.class);
      return resolve == null ? null : PyiUtil.getOriginalElementOrLeaveAsIs(resolve, PyElement.class);
    }
  }
}
