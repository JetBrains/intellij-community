// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyStringLiteralCoreUtil;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class PyStringLiteralExpressionManipulator extends AbstractElementManipulator<PyStringLiteralExpressionImpl> {

  @Override
  public PyStringLiteralExpressionImpl handleContentChange(@NotNull PyStringLiteralExpressionImpl element,
                                                           @NotNull TextRange range,
                                                           String newContent) {
    final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(element.getProject());
    final String escapedText = calculateEscapedText(element.getText(), range, newContent);

    final PyStringLiteralExpression escaped = elementGenerator.createStringLiteralAlreadyEscaped(escapedText);

    return (PyStringLiteralExpressionImpl)element.replace(escaped);
  }

  @Override
  public PyStringLiteralExpressionImpl handleContentChange(@NotNull PyStringLiteralExpressionImpl element, String newContent)
    throws IncorrectOperationException {
    return handleContentChange(element, TextRange.create(0, element.getTextLength()), newContent);
  }

  @Override
  public @NotNull TextRange getRangeInElement(@NotNull PyStringLiteralExpressionImpl element) {
    return element.getStringValueTextRange();
  }

  private static @NotNull String calculateEscapedText(@NotNull String prevText,
                                                      @NotNull TextRange range,
                                                      String newContent) {
    final String newText = range.replace(prevText, newContent);

    if (PyStringLiteralCoreUtil.isQuoted(newText)) {
      return newText;
    }

    final Pair<String, String> quotes = calculateQuotes(prevText);
    return quotes.first + newText + quotes.second;
  }

  private static @NotNull Pair<String, String> calculateQuotes(@NotNull String text) {
    final Pair<String, String> quotes = PyStringLiteralCoreUtil.getQuotes(text);

    if (quotes == null || quotes.first == null && quotes.second == null) return Pair.createNonNull("\"", "\"");

    if (quotes.first == null) return Pair.createNonNull(quotes.second, quotes.second);
    if (quotes.second == null) return Pair.createNonNull(quotes.first, quotes.first);

    return quotes;
  }
}
