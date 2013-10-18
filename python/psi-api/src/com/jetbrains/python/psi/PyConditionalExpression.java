package com.jetbrains.python.psi;

import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PyConditionalExpression extends PyExpression {
  PyExpression getTruePart();

  @Nullable
  PyExpression getCondition();

  @Nullable
  PyExpression getFalsePart();
}
