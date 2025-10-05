// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.actions;

import com.intellij.lang.ExpressionTypeProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.documentation.PyTypeRenderer.Feature;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.List;

/**
 * @author Mikhail Golubev
 */
public final class PythonExpressionTypeProvider extends ExpressionTypeProvider<PyExpression> {
  @Override
  public @NotNull String getInformationHint(@NotNull PyExpression element) {
    TypeEvalContext context = TypeEvalContext.userInitiated(element.getProject(), element.getContainingFile());
    return PythonDocumentationProvider.getTypeName(context.getType(element), context);
  }

  @Override
  public @NotNull String getErrorHint() {
    return PyBundle.message("show.expression.type.no.expression.found");
  }

  @Override
  public @NotNull List<PyExpression> getExpressionsAt(@NotNull PsiElement elementAt) {
    return SyntaxTraverser.psiApi()
      .parents(elementAt)
      .takeWhile(e -> !(e instanceof PsiFile))
      .filter(PyExpression.class)
      .toList();
  }

  @Override
  public boolean hasAdvancedInformation() {
    return ApplicationManager.getApplication().isInternal();
  }

  @Override
  public @NotNull @NlsSafe String getAdvancedInformationHint(@NotNull PyExpression element) {
    @NotNull TypeEvalContext codeAnalysis = TypeEvalContext.codeAnalysis(element.getProject(), element.getContainingFile());
    @NotNull TypeEvalContext userInitiated = TypeEvalContext.userInitiated(element.getProject(), element.getContainingFile());
    return """
      TypeEvalContext.userInitiated: %s
      TypeEvalContext.codeAnalysis: %s
      """.formatted(
      PythonDocumentationProvider.getTypeName(userInitiated.getType(element), userInitiated, Feature.UNSAFE_UNION),
      PythonDocumentationProvider.getTypeName(codeAnalysis.getType(element), codeAnalysis, Feature.UNSAFE_UNION)
    );
  }
}
