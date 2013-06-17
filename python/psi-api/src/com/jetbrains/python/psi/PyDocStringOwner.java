package com.jetbrains.python.psi;

import org.jetbrains.annotations.Nullable;

public interface PyDocStringOwner extends PyElement {
  @Nullable
  String getDocStringValue();

  @Nullable
  StructuredDocString getStructuredDocString();

  @Nullable
  PyStringLiteralExpression getDocStringExpression();
}
