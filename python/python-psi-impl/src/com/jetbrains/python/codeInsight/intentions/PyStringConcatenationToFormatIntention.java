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
import com.jetbrains.python.psi.types.PyClassTypeImpl;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeChecker;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

public final class PyStringConcatenationToFormatIntention extends PsiUpdateModCommandAction<PyBinaryExpression> {

  PyStringConcatenationToFormatIntention() {
    super(PyBinaryExpression.class);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("INTN.string.concatenation.to.format");
  }


  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PyBinaryExpression element) {
    if (!(context.file() instanceof PyFile)) {
      return null;
    }

    PyBinaryExpression topmostBinaryExpr = getTopmostBinaryExpression(element);

    final Collection<PyElementType> operators = getOperators(topmostBinaryExpr);
    for (PyElementType operator : operators) {
      if (operator != PyTokenTypes.PLUS) {
        return null;
      }
    }

    final Collection<PyExpression> expressions = getSimpleExpressions(topmostBinaryExpr);
    if (expressions.isEmpty()) {
      return null;
    }
    LanguageLevel languageLevel = LanguageLevel.forElement(element);
    TypeEvalContext typeEvalContext = TypeEvalContext.codeAnalysis(context.project(), context.file());
    final PyBuiltinCache cache = PyBuiltinCache.getInstance(element);
    for (PyExpression expression : expressions) {
      if (expression == null) {
        return null;
      }
      if (expression instanceof PyStringLiteralExpression) {
        continue;
      }
      final PyType type = typeEvalContext.getType(expression);
      final boolean isStringReference = type != null && PyTypeChecker.match(cache.getStringType(languageLevel), type, typeEvalContext);
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

    final LanguageLevel languageLevel = LanguageLevel.forElement(topmostBinaryExpr);
    final boolean useFormatMethod = languageLevel.isAtLeast(LanguageLevel.PYTHON27);

    Function<String, String> escaper = StringUtil.escaper(false, "\"'\\");
    StringBuilder stringLiteral = new StringBuilder();
    List<String> parameters = new ArrayList<>();
    Pair<String, String> quotes = Pair.create("\"", "\"");
    boolean quotesDetected = false;
    final TypeEvalContext context = TypeEvalContext.userInitiated(actionContext.project(), actionContext.file());
    int paramCount = 0;
    boolean isUnicode = false;
    final PyClassTypeImpl unicodeType = PyBuiltinCache.getInstance(topmostBinaryExpr).getObjectType("unicode");

    for (PyExpression expression : getSimpleExpressions(topmostBinaryExpr)) {
      if (expression instanceof PyStringLiteralExpression) {
        final PyType type = context.getType(expression);
        if (type != null && type.equals(unicodeType)) {
          isUnicode = true;
        }
        if (!quotesDetected) {
          quotes = PyStringLiteralCoreUtil.getQuotes(expression.getText());
          quotesDetected = true;
        }
        String value = ((PyStringLiteralExpression)expression).getStringValue();
        if (!useFormatMethod) {
          value = value.replace("%", "%%");
        }
        stringLiteral.append(escaper.apply(value));
      }
      else {
        addParamToString(stringLiteral, paramCount, useFormatMethod);
        parameters.add(expression.getText());
        ++paramCount;
      }
    }
    if (quotes == null) {
      quotes = Pair.create("\"", "\"");
    }
    stringLiteral.insert(0, quotes.getFirst());
    if (isUnicode && !StringUtil.toLowerCase(quotes.getFirst()).contains("u")) {
      stringLiteral.insert(0, "u");
    }
    stringLiteral.append(quotes.getSecond());

    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(actionContext.project());

    if (!parameters.isEmpty()) {
      if (useFormatMethod) {
        stringLiteral.append(".format(").append(StringUtil.join(parameters, ",")).append(")");
      }
      else {
        final String paramString = parameters.size() > 1 ? "(" + StringUtil.join(parameters, ",") + ")"
                                                         : StringUtil.join(parameters, ",");
        stringLiteral.append(" % ").append(paramString);
      }
      final PyExpression expression = elementGenerator.createFromText(LanguageLevel.getDefault(),
                                                                      PyExpressionStatement.class, stringLiteral.toString())
        .getExpression();
      topmostBinaryExpr.replace(expression);
    }
    else {
      PyStringLiteralExpression stringLiteralExpression =
        elementGenerator.createStringLiteralAlreadyEscaped(stringLiteral.toString());
      topmostBinaryExpr.replace(stringLiteralExpression);
    }
  }

  private static @NotNull PyBinaryExpression getTopmostBinaryExpression(@NotNull PyBinaryExpression element) {
    PyBinaryExpression result = element;
    while (result.getParent() instanceof PyBinaryExpression binaryExpression) {
      result = binaryExpression;
    }
    return result;
  }

  private static Collection<PyExpression> getSimpleExpressions(@NotNull PyBinaryExpression expression) {
    List<PyExpression> res = new ArrayList<>();
    if (expression.getLeftExpression() instanceof PyBinaryExpression) {
      res.addAll(getSimpleExpressions((PyBinaryExpression)expression.getLeftExpression()));
    }
    else {
      res.add(expression.getLeftExpression());
    }
    if (expression.getRightExpression() instanceof PyBinaryExpression) {
      res.addAll(getSimpleExpressions((PyBinaryExpression)expression.getRightExpression()));
    }
    else {
      res.add(expression.getRightExpression());
    }
    return res;
  }

  private static Collection<PyElementType> getOperators(@NotNull PyBinaryExpression expression) {
    List<PyElementType> res = new ArrayList<>();
    if (expression.getLeftExpression() instanceof PyBinaryExpression) {
      res.addAll(getOperators((PyBinaryExpression)expression.getLeftExpression()));
    }
    if (expression.getRightExpression() instanceof PyBinaryExpression) {
      res.addAll(getOperators((PyBinaryExpression)expression.getRightExpression()));
    }
    res.add(expression.getOperator());
    return res;
  }

  private static void addParamToString(StringBuilder stringLiteral, int i, boolean useFormatMethod) {
    if (useFormatMethod) {
      stringLiteral.append("{").append(i).append("}");
    }
    else {
      stringLiteral.append("%s");
    }
  }
}
