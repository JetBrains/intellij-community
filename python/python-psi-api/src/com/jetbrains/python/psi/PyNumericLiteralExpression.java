/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.psi;

import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;

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
  @Nullable
  BigInteger getBigIntegerValue();

  /**
   * Returns the exact value of this literal.
   */
  @Nullable
  BigDecimal getBigDecimalValue();

  boolean isIntegerLiteral();

  /**
   * Returns possible suffix of integer literal like {@code uL}.
   *
   * @return null if this is not integer literal or the suffix is missed; string suffix otherwise
   */
  @Nullable
  String getIntegerLiteralSuffix();
}
