package com.jetbrains.python.psi;

import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PyWithItem extends PyElement {
  PyWithItem[] EMPTY_ARRAY = new PyWithItem[0];

  @Nullable
  PyExpression getExpression();

  @Nullable
  PyExpression getTarget();
}
