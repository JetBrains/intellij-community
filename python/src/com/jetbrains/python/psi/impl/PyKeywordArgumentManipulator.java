package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyKeywordArgument;

/**
 * @author yole
 */
public class PyKeywordArgumentManipulator extends AbstractElementManipulator<PyKeywordArgument> {
  @Override
  public PyKeywordArgument handleContentChange(PyKeywordArgument element, TextRange range, String newContent) throws IncorrectOperationException {
    final ASTNode keywordNode = element.getKeywordNode();
    if (keywordNode != null && keywordNode.getTextRange().shiftRight(-element.getTextRange().getStartOffset()).equals(range)) {
      final LanguageLevel langLevel = LanguageLevel.forElement(element);
      final PyElementGenerator generator = PyElementGenerator.getInstance(element.getProject());
      final PyCallExpression callExpression = (PyCallExpression) generator.createExpressionFromText(langLevel, "foo(" + newContent + "=None)");
      final PyKeywordArgument kwArg = callExpression.getArgumentList().getKeywordArgument(newContent);
      element.getKeywordNode().getPsi().replace(kwArg.getKeywordNode().getPsi());
      return element;
    }
    throw new IncorrectOperationException("unsupported manipulation on keyword argument");
  }
}
