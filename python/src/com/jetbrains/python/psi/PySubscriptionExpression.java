package com.jetbrains.python.psi;

import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PySubscriptionExpression extends PyExpression {
  PyExpression getOperand();

  @Nullable
  PyExpression getIndexExpression();
}
