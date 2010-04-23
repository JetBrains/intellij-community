package com.jetbrains.python.psi;

import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.math.BigDecimal;

public interface PyNumericLiteralExpression extends PyLiteralExpression {
  /**
   * Returns the value of this literal as a long (with any fraction truncated).
   * This method will return {@code null} if the value is too large or too
   * small to be represented as a long.
   */
  @Nullable
  Long getLongValue();

  /**
   * Returns the value of this literal as a {@code BigInteger} (with any
   * fraction truncated).
   */
  BigInteger getBigIntegerValue();

  /**
   * Returns the exact value of this literal.
   */
  BigDecimal getBigDecimalValue();

  boolean isIntegerLiteral();
}
