package com.jetbrains.python.psi;

import org.jetbrains.annotations.Nullable;

/**
 * Main part of a 'for' statement
 * User: dcheryasov
 * Date: Mar 15, 2009 8:55:22 PM
 */
public interface PyForPart extends PyStatementPart {
  /**
   * @return target: the "x" in "<code>for x in (1, 2, 3)</code>".
   */
  @Nullable
  PyExpression getTarget();

  /**
   * @return source of iteration: the "(1, 2, 3)" in "<code>for x in (1, 2, 3)</code>".
   */
  @Nullable
  PyExpression getSource();

}
