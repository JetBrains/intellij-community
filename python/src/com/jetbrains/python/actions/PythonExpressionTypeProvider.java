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
package com.jetbrains.python.actions;

import com.intellij.lang.ExpressionTypeProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Mikhail Golubev
 */
public final class PythonExpressionTypeProvider extends ExpressionTypeProvider<PyExpression> {
  @NotNull
  @Override
  public String getInformationHint(@NotNull PyExpression element) {
    final TypeEvalContext context = TypeEvalContext.userInitiated(element.getProject(), element.getContainingFile());
    return PythonDocumentationProvider.getTypeName(context.getType(element), context);
  }

  @NotNull
  @Override
  public String getErrorHint() {
    return PyBundle.message("show.expression.type.no.expression.found");
  }

  @NotNull
  @Override
  public List<PyExpression> getExpressionsAt(@NotNull PsiElement elementAt) {
    return SyntaxTraverser.psiApi()
      .parents(elementAt)
      .takeWhile(e -> !(e instanceof PsiFile))
      .filter(PyExpression.class)
      .toList();
  }
}
