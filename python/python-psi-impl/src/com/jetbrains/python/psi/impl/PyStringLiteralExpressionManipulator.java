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
package com.jetbrains.python.psi.impl;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyStringLiteralCoreUtil;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;

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

  @NotNull
  @Override
  public TextRange getRangeInElement(@NotNull PyStringLiteralExpressionImpl element) {
    return element.getStringValueTextRange();
  }

  @NotNull
  private static String calculateEscapedText(@NotNull String prevText,
                                             @NotNull TextRange range,
                                             String newContent) {
    final String newText = range.replace(prevText, newContent);

    if (PyStringLiteralCoreUtil.isQuoted(newText)) {
      return newText;
    }

    final Pair<String, String> quotes = calculateQuotes(prevText);
    return quotes.first + newText + quotes.second;
  }

  @NotNull
  private static Pair<String, String> calculateQuotes(@NotNull String text) {
    final Pair<String, String> quotes = PyStringLiteralCoreUtil.getQuotes(text);

    if (quotes == null || quotes.first == null && quotes.second == null) return Pair.createNonNull("\"", "\"");

    if (quotes.first == null) return Pair.createNonNull(quotes.second, quotes.second);
    if (quotes.second == null) return Pair.createNonNull(quotes.first, quotes.first);

    return quotes;
  }
}
