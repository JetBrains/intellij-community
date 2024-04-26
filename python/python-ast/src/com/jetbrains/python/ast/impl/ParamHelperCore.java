package com.jetbrains.python.ast.impl;

import com.intellij.openapi.util.Pair;
import com.jetbrains.python.ast.PyAstExpression;
import com.jetbrains.python.ast.PyAstStringLiteralExpression;
import com.jetbrains.python.psi.PyStringLiteralCoreUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public final class ParamHelperCore {
  private ParamHelperCore() {
  }

  /**
   * @param defaultValue expression returned by {@link PyCallableParameter#getDefaultValue()} or {@link PyParameter#getDefaultValue()}.
   * @return {@code defaultValue} value surrounded by quotes if it is a multi-line string literal, {@code defaultValue} text otherwise.
   */
  @Nullable
  public static String getDefaultValueText(@Nullable PyAstExpression defaultValue) {
    if (defaultValue instanceof PyAstStringLiteralExpression) {
      final Pair<String, String> quotes = PyStringLiteralCoreUtil.getQuotes(defaultValue.getText());
      if (quotes != null) {
        return quotes.getFirst() + ((PyAstStringLiteralExpression)defaultValue).getStringValue() + quotes.getSecond();
      }
    }

    return defaultValue == null ? null : defaultValue.getText();
  }
}
