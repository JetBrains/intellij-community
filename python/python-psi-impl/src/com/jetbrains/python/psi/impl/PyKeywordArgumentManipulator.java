// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyKeywordArgument;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;


@ApiStatus.Internal
public final class PyKeywordArgumentManipulator extends AbstractElementManipulator<PyKeywordArgument> {
  @Override
  public PyKeywordArgument handleContentChange(@NotNull PyKeywordArgument element, @NotNull TextRange range, String newContent)
    throws IncorrectOperationException {
    final ASTNode keywordNode = element.getKeywordNode();
    if (keywordNode != null && keywordNode.getPsi().getTextRangeInParent().equals(range)) {
      final LanguageLevel langLevel = LanguageLevel.forElement(element);
      final PyElementGenerator generator = PyElementGenerator.getInstance(element.getProject());
      final PyCallExpression callExpression =
        (PyCallExpression)generator.createExpressionFromText(langLevel, "foo(" + newContent + "=None)");
      final PyKeywordArgument kwArg = callExpression.getArgumentList().getKeywordArgument(newContent);
      element.getKeywordNode().getPsi().replace(kwArg.getKeywordNode().getPsi());
      return element;
    }
    throw new IncorrectOperationException("unsupported manipulation on keyword argument");
  }
}
