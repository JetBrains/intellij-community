package com.jetbrains.python.psi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PySliceExpression extends PyExpression {
  @NotNull
  PyExpression getOperand();

  @Nullable
  PySliceItem getSliceItem();
}
