package com.jetbrains.python.psi.impl;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.jetbrains.python.PythonStringUtil;
import com.jetbrains.python.psi.PyElementGenerator;

/**
 * @author traff
 */
public class PyStringLiteralExpressionManipulator extends AbstractElementManipulator<PyStringLiteralExpressionImpl> {

  public PyStringLiteralExpressionImpl handleContentChange(PyStringLiteralExpressionImpl element, TextRange range, String newContent) {
    Pair<String, String> quotes = PythonStringUtil.getQuotes(range.substring(element.getText()));

    if (quotes != null) {
      range = TextRange.create(range.getStartOffset() + quotes.first.length(), range.getEndOffset() - quotes.second.length());
    }

    String newName = range.replace(element.getText(), newContent);

    return (PyStringLiteralExpressionImpl)element
      .replace(PyElementGenerator.getInstance(element.getProject()).createStringLiteralAlreadyEscaped(newName));
  }

  @Override
  public TextRange getRangeInElement(PyStringLiteralExpressionImpl element) {
    Pair<String, String> pair = PythonStringUtil.getQuotes(element.getText());
    if (pair != null) {
      return TextRange.from(pair.first.length(), element.getTextLength() - pair.first.length() - pair.second.length());
    }
    return super.getRangeInElement(element);
  }
}
