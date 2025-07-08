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

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.PsiComment;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.codeInsight.intentions.PyTypeHintGenerationUtil;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;


public class PyMakeFunctionReturnTypeQuickFix extends PsiUpdateModCommandAction<PyFunction> {
  private final String myReturnTypeName;
  private final String myReturnTypeFqName;

  public PyMakeFunctionReturnTypeQuickFix(@NotNull PyFunction function, @NotNull TypeEvalContext context) {
    super(function);
    PyType type = getReturnType(function, context);
    myReturnTypeName = PythonDocumentationProvider.getTypeHint(type, context);
    myReturnTypeFqName = PythonDocumentationProvider.getFullyQualifiedTypeHint(type, context);
  }

  private static @Nullable PyType getReturnType(@NotNull PyFunction function, @NotNull TypeEvalContext context) {
    PyType type = function.getInferredReturnType(context);
    if (function.isAsync()) {
      var unwrappedType = PyTypingTypeProvider.unwrapCoroutineReturnType(type);
      if (unwrappedType != null) {
        type = unwrappedType.get();
      }
    }
    return type;
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PyFunction function) {
    return Presentation.of(PyPsiBundle.message("QFIX.make.function.return.type", function.getName(), myReturnTypeName));
  }

  @Override
  public @NotNull String getFamilyName() {
    return PyPsiBundle.message("QFIX.NAME.make.function.return.type");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PyFunction function, @NotNull ModPsiUpdater updater) {
    final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(function.getProject());

    PyAnnotation annotation = function.getAnnotation();
    if (annotation != null) {
      PyExpression annotationExpr = annotation.getValue();
      if (annotationExpr != null) {
        annotationExpr.replace(elementGenerator.createExpressionFromText(LanguageLevel.PYTHON34, myReturnTypeName));
        PyTypeHintGenerationUtil.addImportsForTypeAnnotations(List.of(myReturnTypeFqName), function);
      }
    }
    
    PsiComment typeComment = function.getTypeComment();
    if (typeComment != null) {
      StringBuilder typeCommentAnnotation = new StringBuilder(typeComment.getText());
      typeCommentAnnotation.delete(typeCommentAnnotation.indexOf("->"), typeCommentAnnotation.length());
      typeCommentAnnotation.append("-> ").append(myReturnTypeName);
      typeComment.replace(
        elementGenerator.createFromText(LanguageLevel.PYTHON27, PsiComment.class, typeCommentAnnotation.toString()));
      PyTypeHintGenerationUtil.addImportsForTypeAnnotations(List.of(myReturnTypeFqName), function);
    }
  }
}
