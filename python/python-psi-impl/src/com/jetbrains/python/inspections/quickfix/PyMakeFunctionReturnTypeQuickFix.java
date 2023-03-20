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

import static com.jetbrains.python.inspections.PyTypeCheckerInspection.Visitor.getActualReturnType;

/**
 * @author lada
 */
public class PyMakeFunctionReturnTypeQuickFix implements LocalQuickFix {
  private final @NotNull SmartPsiElementPointer<PyFunction> myFunction;
  private final @Nullable SmartPsiElementPointer<PyExpression> myReturnExpr;
  private final @Nullable SmartPsiElementPointer<PyAnnotation> myAnnotation;
  private final @Nullable SmartPsiElementPointer<PsiComment> myTypeCommentAnnotation;
  private final String myReturnTypeName;
  private final boolean myHaveSuggestedType;

  public PyMakeFunctionReturnTypeQuickFix(@NotNull PyFunction function,
                                          @Nullable PyExpression returnExpr,
                                          @Nullable PyType suggestedReturnType,
                                          @NotNull TypeEvalContext context) {
    this(function,
         returnExpr,
         function.getAnnotation(),
         function.getTypeComment(),
         suggestedReturnType != null,
         getReturnTypeName(function, suggestedReturnType, context));
  }

  @NotNull
  private static String getReturnTypeName(@NotNull PyFunction function, @Nullable PyType returnType, @NotNull TypeEvalContext context) {
    PyType type = returnType != null ? returnType : function.getReturnStatementType(context);
    return PythonDocumentationProvider.getTypeHint(type, context);
  }

  private PyMakeFunctionReturnTypeQuickFix(@NotNull PyFunction function,
                                           @Nullable PyExpression returnExpr,
                                           @Nullable PyAnnotation annotation,
                                           @Nullable PsiComment typeComment,
                                           boolean returnTypeSuggested,
                                           @NotNull String returnTypeName) {
    SmartPointerManager manager = SmartPointerManager.getInstance(function.getProject());
    myFunction = manager.createSmartPsiElementPointer(function);
    myReturnExpr = returnExpr != null ? manager.createSmartPsiElementPointer(returnExpr) : null;
    myAnnotation = annotation != null ? manager.createSmartPsiElementPointer(annotation) : null;
    myTypeCommentAnnotation = typeComment != null ? manager.createSmartPsiElementPointer(typeComment) : null;
    myHaveSuggestedType = returnTypeSuggested;
    myReturnTypeName = returnTypeName;
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
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    if (myAnnotation != null) {
      PyAnnotation annotation = myAnnotation.getElement();
      if (annotation != null) {
        PyExpression annotationExpr = annotation.getValue();
        if (annotationExpr == null) return;
        PsiElement newElement =
          annotationExpr.replace(elementGenerator.createExpressionFromText(LanguageLevel.PYTHON34, myReturnTypeName));
        addImportsForTypeAnnotations(newElement);
      }
    }
    else if (myTypeCommentAnnotation != null) {
      PsiComment typeComment = myTypeCommentAnnotation.getElement();
      if (typeComment != null) {
        StringBuilder typeCommentAnnotation = new StringBuilder(typeComment.getText());
        typeCommentAnnotation.delete(typeCommentAnnotation.indexOf("->"), typeCommentAnnotation.length());
        typeCommentAnnotation.append("-> ").append(myReturnTypeName);
        PsiComment newTypeComment =
          elementGenerator.createFromText(LanguageLevel.PYTHON27, PsiComment.class, typeCommentAnnotation.toString());
        PsiElement newElement = typeComment.replace(newTypeComment);
        addImportsForTypeAnnotations(newElement);
      }
    }
  }

  private void addImportsForTypeAnnotations(@NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();
    if (file == null) return;
    PyFunction function = myFunction.getElement();
    if (function == null) return;
    Project project = element.getProject();
    TypeEvalContext typeEvalContext = TypeEvalContext.userInitiated(project, file);
    PyType typeForImports = getTypeForImports(function, typeEvalContext);
    if (typeForImports != null) {
      PyTypeHintGenerationUtil.addImportsForTypeAnnotations(List.of(typeForImports), typeEvalContext, file);
    }
  }

  private @Nullable PyType getTypeForImports(@NotNull PyFunction function, @NotNull TypeEvalContext context) {
    PyType returnTypeActual = getActualReturnType(function, myReturnExpr != null ? myReturnExpr.getElement() : null, context);
    if (myHaveSuggestedType && returnTypeActual != null) {
      return returnTypeActual;
    }
    else {
      return function.getReturnStatementType(context);
    }
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    PyFunction function = PyRefactoringUtil.findSameElementForPreview(myFunction, target);
    if (function == null) {
      return null;
    }
    @Nullable PyExpression returnExpr = PyRefactoringUtil.findSameElementForPreview(myReturnExpr, target);
    if (myReturnExpr != null && returnExpr == null) {
      return null;
    }
    @Nullable PyAnnotation annotation = PyRefactoringUtil.findSameElementForPreview(myAnnotation, target);
    if (myAnnotation != null && annotation == null) {
      return null;
    }
    @Nullable PsiComment typeComment = PyRefactoringUtil.findSameElementForPreview(myTypeCommentAnnotation, target);
    if (myTypeCommentAnnotation != null && typeComment == null) {
      return null;
    }
    return new PyMakeFunctionReturnTypeQuickFix(function, returnExpr, annotation, typeComment, myHaveSuggestedType, myReturnTypeName);
  }
}
