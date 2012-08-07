package com.jetbrains.python.psi;

import org.jetbrains.annotations.Nullable;

public interface PyDocStringOwner extends PyElement {
  @Nullable
  PyStringLiteralExpression getDocStringExpression();
}
