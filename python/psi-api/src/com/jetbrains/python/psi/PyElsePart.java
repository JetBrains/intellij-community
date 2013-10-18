package com.jetbrains.python.psi;

import org.jetbrains.annotations.Nullable;

/**
 * The 'else:' part of various compound statements.
 * User: dcheryasov
 * Date: Mar 15, 2009 9:34:51 PM
 */
public interface PyElsePart extends PyStatementPart {
  /**
   * @return the body of the 'else' part.
   */
  @Nullable
  PyStatementList getStatementList();
}
