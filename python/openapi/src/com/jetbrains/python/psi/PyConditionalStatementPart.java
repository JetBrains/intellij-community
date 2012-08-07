package com.jetbrains.python.psi;

import org.jetbrains.annotations.Nullable;

/**
 * A statement part that has a condition before it.
 * User: dcheryasov
 * Date: Mar 16, 2009 4:44:25 AM
 */
public interface PyConditionalStatementPart extends PyStatementPart {
  /**
   * @return the condition expression.
   */
  @Nullable
  PyExpression getCondition();
}
