package com.jetbrains.python.ast.impl;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.ast.PyAstExpression;
import com.jetbrains.python.ast.PyAstNamedParameter;
import com.jetbrains.python.ast.PyAstParameter;
import com.jetbrains.python.ast.PyAstSingleStarParameter;
import com.jetbrains.python.ast.PyAstSlashParameter;
import com.jetbrains.python.ast.PyAstStringLiteralExpression;
import com.jetbrains.python.ast.PyAstTupleParameter;
import com.jetbrains.python.psi.PyStringLiteralCoreUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public final class ParamHelperCore {
  private ParamHelperCore() {
  }

  public static @NotNull String getNameInSignature(@NotNull PyAstNamedParameter parameter) {
    final StringBuilder sb = new StringBuilder();

    if (parameter.isPositionalContainer()) sb.append("*");
    else if (parameter.isKeywordContainer()) sb.append("**");

    final String name = parameter.getName();
    sb.append(name != null ? name : "...");

    return sb.toString();
  }

  /**
   * @param defaultValue             string returned by {@link PyCallableParameter#getDefaultValueText()} or {@link PyParameter#getDefaultValueText()}.
   * @param parameterRenderedAsTyped true if parameter is rendered with type annotation.
   * @return equal sign (surrounded with spaces if {@code parameterRenderedAsTyped}) +
   * {@code defaultValue} (with body escaped if it is a string literal)
   */
  @Contract("null, _->null")
  public static @Nullable String getDefaultValuePartInSignature(@Nullable String defaultValue, boolean parameterRenderedAsTyped) {
    if (defaultValue == null) return null;

    final StringBuilder sb = new StringBuilder();

    // According to PEP 8 equal sign should be surrounded by spaces if annotation is present
    sb.append(parameterRenderedAsTyped ? " = " : "=");

    final Pair<String, String> quotes = PyStringLiteralCoreUtil.getQuotes(defaultValue);
    if (quotes != null) {
      final String value = defaultValue.substring(quotes.getFirst().length(), defaultValue.length() - quotes.getSecond().length());
      sb.append(quotes.getFirst());
      StringUtil.escapeStringCharacters(value.length(), value, sb);
      sb.append(quotes.getSecond());
    }
    else {
      sb.append(defaultValue);
    }

    return sb.toString();
  }

  /**
   * @param defaultValue expression returned by {@link PyCallableParameter#getDefaultValue()} or {@link PyParameter#getDefaultValue()}.
   * @return {@code defaultValue} value surrounded by quotes if it is a multi-line string literal, {@code defaultValue} text otherwise.
   */
  public static @Nullable String getDefaultValueText(@Nullable PyAstExpression defaultValue) {
    if (defaultValue instanceof PyAstStringLiteralExpression) {
      final Pair<String, String> quotes = PyStringLiteralCoreUtil.getQuotes(defaultValue.getText());
      if (quotes != null) {
        return quotes.getFirst() + ((PyAstStringLiteralExpression)defaultValue).getStringValue() + quotes.getSecond();
      }
    }

    return defaultValue == null ? null : defaultValue.getText();
  }

  /**
   * Visits the parameters of a parameter list in order, and the contents of a tuple parameter between
   * {@link ParamVisitor#enterTupleParameter} and {@link ParamVisitor#leaveTupleParameter}.
   */
  public static void walkDownParamArray(PyAstParameter @NotNull [] parameters, @NotNull ParamVisitor visitor) {
    for (int i = 0; i < parameters.length; i++) {
      final PyAstParameter parameter = parameters[i];
      final boolean first = i == 0;
      final boolean last = i == parameters.length - 1;
      if (parameter instanceof PyAstTupleParameter tupleParameter) {
        visitor.enterTupleParameter(tupleParameter, first, last);
        walkDownParamArray(tupleParameter.getContents(), visitor);
        visitor.leaveTupleParameter(tupleParameter, first, last);
      }
      else if (parameter instanceof PyAstNamedParameter namedParameter) {
        visitor.visitNamedParameter(namedParameter, first, last);
      }
      else if (parameter instanceof PyAstSlashParameter slashParameter) {
        visitor.visitSlashParameter(slashParameter, first, last);
      }
      else if (parameter instanceof PyAstSingleStarParameter singleStarParameter) {
        visitor.visitSingleStarParameter(singleStarParameter, first, last);
      }
    }
  }

  /** The callbacks of {@link #walkDownParamArray}. {@code first} and {@code last} tell the position in the enclosing list. */
  public abstract static class ParamVisitor {
    public void enterTupleParameter(PyAstTupleParameter parameter, boolean first, boolean last) { }

    public void leaveTupleParameter(PyAstTupleParameter parameter, boolean first, boolean last) { }

    public void visitNamedParameter(PyAstNamedParameter parameter, boolean first, boolean last) { }

    public void visitSlashParameter(PyAstSlashParameter parameter, boolean first, boolean last) { }

    public void visitSingleStarParameter(PyAstSingleStarParameter parameter, boolean first, boolean last) { }
  }
}
