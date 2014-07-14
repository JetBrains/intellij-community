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
package com.jetbrains.python.psi.impl;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.jetbrains.python.PythonStringUtil;
import com.jetbrains.python.psi.PyElementGenerator;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class PyStringLiteralExpressionManipulator extends AbstractElementManipulator<PyStringLiteralExpressionImpl> {

  @Override
  public PyStringLiteralExpressionImpl handleContentChange(@NotNull PyStringLiteralExpressionImpl element, @NotNull TextRange range, String newContent) {
    Pair<String, String> quotes = PythonStringUtil.getQuotes(range.substring(element.getText()));

    if (quotes != null) {
      range = TextRange.create(range.getStartOffset() + quotes.first.length(), range.getEndOffset() - quotes.second.length());
    }

    String newName = range.replace(element.getText(), newContent);

    return (PyStringLiteralExpressionImpl)element
      .replace(PyElementGenerator.getInstance(element.getProject()).createStringLiteralAlreadyEscaped(newName));
  }

  @NotNull
  @Override
  public TextRange getRangeInElement(@NotNull PyStringLiteralExpressionImpl element) {
    Pair<String, String> pair = PythonStringUtil.getQuotes(element.getText());
    if (pair != null) {
      return TextRange.from(pair.first.length(), element.getTextLength() - pair.first.length() - pair.second.length());
    }
    return super.getRangeInElement(element);
  }
}
