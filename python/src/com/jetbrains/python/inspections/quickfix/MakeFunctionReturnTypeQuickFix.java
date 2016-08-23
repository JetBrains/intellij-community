/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiComment;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyFunctionImpl;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author lada
 */
public class MakeFunctionReturnTypeQuickFix implements LocalQuickFix {
  SmartPsiElementPointer<PyFunction> myFunction;
  SmartPsiElementPointer<PyAnnotation> myAnnotation;
  SmartPsiElementPointer<PsiComment> myTypeCommentAnnotation;
  PyType myReturnType;
  TypeEvalContext myTypeEvalContext;

  public MakeFunctionReturnTypeQuickFix(PyFunction function, PyType returnType, TypeEvalContext context) {
    myFunction = SmartPointerManager.getInstance(function.getProject()).createSmartPsiElementPointer(function);
    PyAnnotation annotation = function.getAnnotation();
    myAnnotation = annotation != null ? SmartPointerManager.getInstance(function.getProject()).createSmartPsiElementPointer(annotation) : null;
    PsiComment typeCommentAnnotation = function.getTypeComment();
    myTypeCommentAnnotation = typeCommentAnnotation != null ? SmartPointerManager.getInstance(function.getProject()).createSmartPsiElementPointer(typeCommentAnnotation) : null;
    myTypeEvalContext = context;
    myReturnType = (returnType == null && function instanceof PyFunctionImpl) ? ((PyFunctionImpl)function).getReturnStatementType(myTypeEvalContext) : returnType;
  }

  @NotNull
  public String getName() {
    PyFunction function = myFunction.getElement();
    return String.format("Make '%s' return '%s'",
                         function != null ? function.getName() : "function",
                         PythonDocumentationProvider.getTypeName(myReturnType, myTypeEvalContext));
  }

  @NotNull
  public String getFamilyName() {
    return "Make function return inferred type";
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    if (myAnnotation != null) {
      final PyAnnotation annotation = myAnnotation.getElement();
      if (annotation != null) {
        final PyExpression annotationExpr = annotation.getValue();
        if (annotationExpr == null) return;
        annotationExpr.replace(elementGenerator.createExpressionFromText(LanguageLevel.PYTHON30,
                                                                         PythonDocumentationProvider.getTypeName(myReturnType, myTypeEvalContext)));
      }
    }
    else if (myTypeCommentAnnotation != null) {
      final PsiComment typeComment = myTypeCommentAnnotation.getElement();
      if (typeComment != null) {
        final StringBuilder typeCommentAnnotation = new StringBuilder(typeComment.getText());
        typeCommentAnnotation.delete(typeCommentAnnotation.indexOf("->"), typeCommentAnnotation.length());
        typeCommentAnnotation.append("-> ").append(PythonDocumentationProvider.getTypeName(myReturnType, myTypeEvalContext));
        final PsiComment newTypeComment = elementGenerator.createFromText(LanguageLevel.PYTHON27, PsiComment.class, typeCommentAnnotation.toString());
        typeComment.replace(newTypeComment);
      }
    }
  }
}
