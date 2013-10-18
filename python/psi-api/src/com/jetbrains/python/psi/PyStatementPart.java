package com.jetbrains.python.psi;

import org.jetbrains.annotations.Nullable;

/**
 * Abstract part of a multipart statement.
 * User: dcheryasov
 * Date: Mar 16, 2009 4:34:59 AM
 */
public interface PyStatementPart extends PyElement {
  PyStatementPart[] EMPTY_ARRAY = new PyStatementPart[0];

  /**
   * @return the body of the part.
   */
  @Nullable
  PyStatementList getStatementList();
}
