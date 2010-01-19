package com.jetbrains.python.psi;

import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PySliceExpression extends PyExpression {
  PyExpression getOperand();
  PyExpression getLowerBound();
  PyExpression getUpperBound();
  
  @Nullable
  PyExpression getStride();
}
