// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeChecker;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class PyStringConcatenationToFormatIntention extends PsiUpdateModCommandAction<PyBinaryExpression> {

  PyStringConcatenationToFormatIntention() {
    super(PyBinaryExpression.class);
  }

  @Override
  public @NotNull String getFamilyName() {
    return PyPsiBundle.message("INTN.string.concatenation.to.format");
  }


  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PyBinaryExpression element) {
    if (!(context.file() instanceof PyFile)) {
      return null;
    }

    PyBinaryExpression topmostBinaryExpr = getTopmostBinaryExpression(element);

    List<PyElementType> operators = getOperators(topmostBinaryExpr);
    for (PyElementType operator : operators) {
      if (operator != PyTokenTypes.PLUS) {
        return null;
      }
    }

    List<PyExpression> operands = getOperands(topmostBinaryExpr);
    if (operands.isEmpty()) {
      return null;
    }
    LanguageLevel languageLevel = LanguageLevel.forElement(element);
    TypeEvalContext typeEvalContext = TypeEvalContext.codeAnalysis(context.project(), context.file());
    PyType stringType = PyBuiltinCache.getInstance(element).getStringType(languageLevel);
    for (PyExpression operand : operands) {
      if (operand == null) {
        return null;
      }
      if (operand instanceof PyStringLiteralExpression) {
        continue;
      }
      PyType operandType = typeEvalContext.getType(operand);
      boolean isStringReference = operandType != null && PyTypeChecker.match(stringType, operandType, typeEvalContext);
      if (!isStringReference) {
        return null;
      }
    }
    if (languageLevel.isAtLeast(LanguageLevel.PYTHON27)) {
      return Presentation.of(PyPsiBundle.message("INTN.replace.plus.with.str.format"));
    }
    else {
      return Presentation.of(PyPsiBundle.message("INTN.replace.plus.with.format.operator"));
    }
  }

  @Override
  protected void invoke(@NotNull ActionContext actionContext, @NotNull PyBinaryExpression element, @NotNull ModPsiUpdater updater) {
    PyBinaryExpression topmostBinaryExpr = getTopmostBinaryExpression(element);

    LanguageLevel languageLevel = LanguageLevel.forElement(topmostBinaryExpr);
    boolean useFormatMethod = languageLevel.isAtLeast(LanguageLevel.PYTHON27);

    Function<String, String> escaper = StringUtil.escaper(false, "\"'\\");
    StringBuilder stringFormatExpr = new StringBuilder();
    List<String> parameters = new ArrayList<>();
    Pair<String, String> quotes = Pair.create("\"", "\"");
    boolean quotesDetected = false;
    TypeEvalContext context = TypeEvalContext.userInitiated(actionContext.project(), actionContext.file());
    boolean isUnicode = false;
    PyClassType unicodeType = PyBuiltinCache.getInstance(topmostBinaryExpr).getObjectType("unicode");

    for (PyExpression operand : getOperands(topmostBinaryExpr)) {
      if (operand instanceof PyStringLiteralExpression stringLiteralOperand) {
        PyType type = context.getType(operand);
        if (type != null && type.equals(unicodeType)) {
          isUnicode = true;
        }
        if (!quotesDetected) {
          quotes = PyStringLiteralCoreUtil.getQuotes(operand.getText());
          quotesDetected = true;
        }
        String value = stringLiteralOperand.getStringValue();
        if (!useFormatMethod) {
          value = value.replace("%", "%%");
        }
        stringFormatExpr.append(escaper.apply(value));
      }
      else {
        addParamToString(stringFormatExpr, parameters.size(), useFormatMethod);
        parameters.add(operand.getText());
      }
    }
    if (quotes == null) {
      quotes = Pair.create("\"", "\"");
    }
    stringFormatExpr.insert(0, quotes.getFirst());
    if (isUnicode && !StringUtil.toLowerCase(quotes.getFirst()).contains("u")) {
      stringFormatExpr.insert(0, "u");
    }
    stringFormatExpr.append(quotes.getSecond());

    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(actionContext.project());

    if (!parameters.isEmpty()) {
      if (useFormatMethod) {
        stringFormatExpr.append(".format(").append(StringUtil.join(parameters, ",")).append(")");
      }
      else {
        String paramString = parameters.size() > 1 ? "(" + StringUtil.join(parameters, ",") + ")"
                                                   : StringUtil.join(parameters, ",");
        stringFormatExpr.append(" % ").append(paramString);
      }
      topmostBinaryExpr.replace(elementGenerator.createExpressionFromText(languageLevel, stringFormatExpr.toString()));
    }
    else {
      topmostBinaryExpr.replace(elementGenerator.createStringLiteralAlreadyEscaped(stringFormatExpr.toString()));
    }
  }

  private static @NotNull PyBinaryExpression getTopmostBinaryExpression(@NotNull PyBinaryExpression element) {
    PyBinaryExpression result = element;
    while (result.getParent() instanceof PyBinaryExpression binaryExpression) {
      result = binaryExpression;
    }
    return result;
  }

  private static @NotNull List<PyExpression> getOperands(@NotNull PyBinaryExpression expression) {
    List<PyExpression> res = new ArrayList<>();
    if (expression.getLeftExpression() instanceof PyBinaryExpression leftBinaryOperand) {
      res.addAll(getOperands(leftBinaryOperand));
    }
    else {
      res.add(expression.getLeftExpression());
    }
    if (expression.getRightExpression() instanceof PyBinaryExpression rightBinaryOperand) {
      res.addAll(getOperands(rightBinaryOperand));
    }
    else {
      res.add(expression.getRightExpression());
    }
    return res;
  }

  private static @NotNull List<PyElementType> getOperators(@NotNull PyBinaryExpression expression) {
    List<PyElementType> res = new ArrayList<>();
    if (expression.getLeftExpression() instanceof PyBinaryExpression leftBinaryOperand) {
      res.addAll(getOperators(leftBinaryOperand));
    }
    if (expression.getRightExpression() instanceof PyBinaryExpression rightBinaryOperand) {
      res.addAll(getOperators(rightBinaryOperand));
    }
    res.add(expression.getOperator());
    return res;
  }

  private static void addParamToString(@NotNull StringBuilder stringLiteral, int index, boolean useFormatMethod) {
    if (useFormatMethod) {
      stringLiteral.append("{").append(index).append("}");
    }
    else {
      stringLiteral.append("%s");
    }
  }
}
