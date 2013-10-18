package com.jetbrains.python.psi;

import org.jetbrains.annotations.Nullable;

/**
 * A part of a multi-part statement which can have an "else:" clause.
 * User: dcheryasov
 * Date: Mar 2, 2010 7:01:36 PM
 */
public interface PyStatementWithElse extends PyStatement {
  @Nullable
  PyElsePart getElsePart();
}
