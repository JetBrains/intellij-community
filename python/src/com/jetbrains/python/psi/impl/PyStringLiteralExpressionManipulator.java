package com.jetbrains.python.psi.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.jetbrains.python.PythonStringUtil;
import com.jetbrains.python.psi.PyElementGenerator;

/**
 * @author traff
 */
public class PyStringLiteralExpressionManipulator extends AbstractElementManipulator<PyStringLiteralExpressionImpl> {
  private static final Logger LOG = Logger.getInstance(PyStringLiteralExpressionManipulator.class);

  public PyStringLiteralExpressionImpl handleContentChange(PyStringLiteralExpressionImpl element, TextRange range, String newContent) {
    String newName = range.replace(element.getText(), newContent);
    if (!PythonStringUtil.isQuoted(newName)) {
      LOG.error("Should be quoted: " + newName);
    }
    return (PyStringLiteralExpressionImpl)element
      .replace(PyElementGenerator.getInstance(element.getProject()).createStringLiteralAlreadyEscaped(newName));
  }
}
