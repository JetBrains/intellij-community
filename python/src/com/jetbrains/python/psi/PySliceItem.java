package com.jetbrains.python.psi;

import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PySliceItem extends PyElement {
  @Nullable
  PyExpression getLowerBound();

  @Nullable
  PyExpression getUpperBound();

  @Nullable
  PyExpression getStride();
}
