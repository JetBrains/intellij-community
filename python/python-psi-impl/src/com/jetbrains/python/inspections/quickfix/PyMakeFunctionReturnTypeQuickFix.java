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

import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.codeInsight.intentions.PyTypeHintGenerationUtil;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.refactoring.PyRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;


public class PyMakeFunctionReturnTypeQuickFix implements LocalQuickFix {
  private final @NotNull SmartPsiElementPointer<PyFunction> myFunction;
  private final String myReturnTypeName;

  public PyMakeFunctionReturnTypeQuickFix(@NotNull PyFunction function, @NotNull TypeEvalContext context) {
    this(function, getReturnTypeName(function, context));
  }

  private PyMakeFunctionReturnTypeQuickFix(@NotNull PyFunction function, @NotNull String returnTypeName) {
    SmartPointerManager manager = SmartPointerManager.getInstance(function.getProject());
    myFunction = manager.createSmartPsiElementPointer(function);
    myReturnTypeName = returnTypeName;
  }

  @NotNull
  private static String getReturnTypeName(@NotNull PyFunction function, @NotNull TypeEvalContext context) {
    final PyType type = function.getInferredReturnType(context);
    return PythonDocumentationProvider.getTypeHint(type, context);
  }

  @Override
  @NotNull
  public String getName() {
    PyFunction function = myFunction.getElement();
    String functionName = function != null ? function.getName() : "function";
    return PyPsiBundle.message("QFIX.make.function.return.type", functionName, myReturnTypeName);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("QFIX.NAME.make.function.return.type");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PyFunction function = myFunction.getElement();
    if (function == null) return;
    
    final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);

    boolean shouldAddImports = false;

    PyAnnotation annotation = function.getAnnotation();
    if (annotation != null) {
      PyExpression annotationExpr = annotation.getValue();
      if (annotationExpr != null) {
        annotationExpr.replace(elementGenerator.createExpressionFromText(LanguageLevel.PYTHON34, myReturnTypeName));
        shouldAddImports = true;
      }
    }
    
    PsiComment typeComment = function.getTypeComment();
    if (typeComment != null) {
      StringBuilder typeCommentAnnotation = new StringBuilder(typeComment.getText());
      typeCommentAnnotation.delete(typeCommentAnnotation.indexOf("->"), typeCommentAnnotation.length());
      typeCommentAnnotation.append("-> ").append(myReturnTypeName);
      typeComment.replace(
        elementGenerator.createFromText(LanguageLevel.PYTHON27, PsiComment.class, typeCommentAnnotation.toString()));
      shouldAddImports = true;
    }
    
    if (shouldAddImports) {
      addImportsForTypeAnnotations(TypeEvalContext.userInitiated(project, function.getContainingFile()));
    }
  }

  private void addImportsForTypeAnnotations(@NotNull TypeEvalContext context) {
    PyFunction function = myFunction.getElement();
    if (function == null) return;
    PsiFile file = function.getContainingFile();
    if (file == null) return;
    
    PyType typeForImports = function.getInferredReturnType(context);
    if (typeForImports != null) {
      PyTypeHintGenerationUtil.addImportsForTypeAnnotations(List.of(typeForImports), context, file);
    }
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    PyFunction function = PyRefactoringUtil.findSameElementForPreview(myFunction, target);
    if (function == null) {
      return null;
    }
    return new PyMakeFunctionReturnTypeQuickFix(function, myReturnTypeName);
  }
}
