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
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyDeprecationInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Deprecated function, class or module";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        final boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  private static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyReferenceExpression(PyReferenceExpression node) {
      final PyExceptPart exceptPart = PsiTreeUtil.getParentOfType(node, PyExceptPart.class);
      if (exceptPart != null) {
        final PyExpression exceptClass = exceptPart.getExceptClass();
        if (exceptClass != null && "ImportError".equals(exceptClass.getText())) return;
      }
      final PsiPolyVariantReference reference = node.getReference(getResolveContext());
      if (reference == null) return;
      final PsiElement resolveResult = reference.resolve();
      final PyFromImportStatement importStatement = PsiTreeUtil.getParentOfType(node, PyFromImportStatement.class);
      if (importStatement != null) {
        final PsiElement element = importStatement.resolveImportSource();
        if (resolveResult != null && element != resolveResult.getContainingFile()) return;
      }
      String deprecationMessage = null;
      if (resolveResult instanceof PyFunction) {
        deprecationMessage = ((PyFunction) resolveResult).getDeprecationMessage();
      }
      else if (resolveResult instanceof PyFile) {
        deprecationMessage = ((PyFile)resolveResult).getDeprecationMessage();
      }
      if (deprecationMessage != null) {
        ASTNode nameElement = node.getNameElement();
        registerProblem(nameElement == null ? node : nameElement.getPsi(), deprecationMessage, ProblemHighlightType.LIKE_DEPRECATED);
      }
    }
  }
}
