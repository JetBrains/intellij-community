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
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyNumericLiteralExpression;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;

/**
 * @author yole
 */
public class PyNumericLiteralExpressionImpl extends PyElementImpl implements PyNumericLiteralExpression {

  public PyNumericLiteralExpressionImpl(@NotNull ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyNumericLiteralExpression(this);
  }

  @Override
  @Nullable
  public Long getLongValue() {
    final BigInteger value = getBigIntegerValue();

    return Optional
      .ofNullable(value)
      .map(BigInteger::longValue)
      .filter(longValue -> BigInteger.valueOf(longValue).equals(value))
      .orElse(null);
  }

  @Override
  @Nullable
  public BigInteger getBigIntegerValue() {
    if (isIntegerLiteral()) {
      return getBigIntegerValue(getNode().getText());
    }

    final BigDecimal bigDecimal = getBigDecimalValue();
    return bigDecimal == null ? null : bigDecimal.toBigInteger();
  }

  @Override
  @Nullable
  public BigDecimal getBigDecimalValue() {
    final String text = getNode().getText();

    if (isIntegerLiteral()) {
      return Optional
        .ofNullable(getBigIntegerValue(text))
        .map(BigDecimal::new)
        .orElse(null);
    }

    return new BigDecimal(prepareLiteralForJava(text, 0));
  }

  @Override
  public boolean isIntegerLiteral() {
    return getNode().getElementType() == PyElementTypes.INTEGER_LITERAL_EXPRESSION;
  }

  @Override
  @Nullable
  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    if (isIntegerLiteral()) {
      return PyBuiltinCache.getInstance(this).getIntType();
    }

    final IElementType type = getNode().getElementType();
    if (type == PyElementTypes.FLOAT_LITERAL_EXPRESSION) {
      return PyBuiltinCache.getInstance(this).getFloatType();
    }
    else if (type == PyElementTypes.IMAGINARY_LITERAL_EXPRESSION) {
      return PyBuiltinCache.getInstance(this).getComplexType();
    }

    return null;
  }

  @Nullable
  private static BigInteger getBigIntegerValue(@NotNull String text) {
    if (text.equals("0") || text.equalsIgnoreCase("0l")) {
      return BigInteger.ZERO;
    }

    final int beginIndex;
    final int radix;

    if (StringUtil.startsWithIgnoreCase(text, "0x")) {
      beginIndex = 2;
      radix = 16;
    }
    else if (StringUtil.startsWithIgnoreCase(text, "0b")) {
      beginIndex = 2;
      radix = 2;
    }
    else if (text.startsWith("0")) {
      if (StringUtil.isChar(text, 1, 'o') || StringUtil.isChar(text, 1, 'O')) {
        beginIndex = 2;
        radix = 8;
      }
      else {
        beginIndex = 1;
        radix = 8;
      }
    }
    else {
      beginIndex = 0;
      radix = 10;
    }

    return new BigInteger(prepareLiteralForJava(text, beginIndex), radix);
  }

  @NotNull
  private static String prepareLiteralForJava(@NotNull String text, int beginIndex) {
    final int endIndex =
      StringUtil.endsWithIgnoreCase(text, "l") || StringUtil.endsWithIgnoreCase(text, "j") ? text.length() - 1 : text.length();

    return text.substring(beginIndex, endIndex).replaceAll("_", "");
  }
}
