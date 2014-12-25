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

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

public class PyAddExceptionSuperClassQuickFix implements LocalQuickFix {

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.NAME.add.exception.base");
  }

  @NotNull
  public String getFamilyName() {
    return getName();
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    if (element instanceof PyCallExpression) {
      PyExpression callee = ((PyCallExpression)element).getCallee();
      if (callee instanceof PyReferenceExpression) {
        final PsiPolyVariantReference reference = ((PyReferenceExpression)callee).getReference();
        PsiElement psiElement = reference.resolve();
        if (psiElement instanceof PyClass) {
          final PyElementGenerator generator = PyElementGenerator.getInstance(project);
          final PyArgumentList list = ((PyClass)psiElement).getSuperClassExpressionList();
          if (list != null) {
            final PyExpression exception =
              generator.createExpressionFromText(LanguageLevel.forElement(element), "Exception");
            list.addArgument(exception);
          }
          else {
            final PyArgumentList expressionList = generator.createFromText(
              LanguageLevel.forElement(element), PyClass.class, "class A(Exception): pass").getSuperClassExpressionList();
            assert expressionList != null;
            final ASTNode nameNode = ((PyClass)psiElement).getNameNode();
            assert nameNode != null;
            psiElement.addAfter(expressionList, nameNode.getPsi());
          }
        }
      }
    }
  }

}
