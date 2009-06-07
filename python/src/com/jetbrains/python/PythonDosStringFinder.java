package com.jetbrains.python;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.Nullable;

// TODO: find a better place for this.
/**
 * Utility class for finding an expression which would fit as a doc string.
 * User: dcheryasov
 * Date: Jun 7, 2009 5:06:12 AM
 */
public class PythonDosStringFinder {
  private PythonDosStringFinder()  {}

  /**
   * Looks for a doc string under given parent.
   * @param parent where to look. For classes and functions, this would be PyStatementList, for modules, PyFile.
   * @return the defining expression, or null.
   */
  @Nullable
  public static PyStringLiteralExpression find(PyElement parent) {
    if (parent != null) {
      PsiElement seeker = PyUtil.getFirstNonCommentAfter(parent.getFirstChild());
      if (seeker instanceof PyExpressionStatement) seeker = PyUtil.getFirstNonCommentAfter(seeker.getFirstChild());
      if (seeker instanceof PyStringLiteralExpression) return (PyStringLiteralExpression)seeker;
    }
    return null;
  }
}
