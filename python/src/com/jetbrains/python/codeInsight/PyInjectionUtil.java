/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.codeInsight;

import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyCallExpressionNavigator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

import static com.jetbrains.python.inspections.PyStringFormatParser.*;

/**
 * @author vlan
 */
public class PyInjectionUtil {

  public static class InjectionResult {
    public static InjectionResult EMPTY = new InjectionResult(false, true);

    private final boolean myInjected;
    private final boolean myStrict;

    public InjectionResult(boolean injected, boolean strict) {
      myInjected = injected;
      myStrict = strict;
    }

    public boolean isInjected() {
      return myInjected;
    }

    public boolean isStrict() {
      return myStrict;
    }

    public InjectionResult append(@NotNull InjectionResult result) {
      return new InjectionResult(myInjected || result.isInjected(), myStrict && result.isStrict());
    }
  }

  public static final List<Class<? extends PsiElement>> ELEMENTS_TO_INJECT_IN =
    Arrays.asList(PyStringLiteralExpression.class, PyParenthesizedExpression.class, PyBinaryExpression.class, PyCallExpression.class,
                  PsiComment.class);

  private PyInjectionUtil() {}

  /**
   * Returns the largest expression in the specified context that represents a string literal suitable for language injection, possibly
   * with concatenation, parentheses, or formatting.
   */
  @Nullable
  public static PsiElement getLargestStringLiteral(@NotNull PsiElement context) {
    PsiElement element = null;
    for (PsiElement current = context; current != null && isStringLiteralPart(current, element); current = current.getParent()) {
      element = current;
    }
    return element;
  }

  /**
   * Registers language injections in the given registrar for the specified string literal element or its ancestor that contains
   * string concatenations or formatting.
   */
  @NotNull
  public static InjectionResult registerStringLiteralInjection(@NotNull PsiElement element, @NotNull MultiHostRegistrar registrar) {
    return processStringLiteral(element, registrar, "", "", Formatting.NONE);
  }

  private static boolean isStringLiteralPart(@NotNull PsiElement element, @Nullable PsiElement context) {
    if (element == context || element instanceof PyStringLiteralExpression || element instanceof PsiComment) {
      return true;
    }
    else if (element instanceof PyParenthesizedExpression) {
      final PyExpression contained = ((PyParenthesizedExpression)element).getContainedExpression();
      return contained != null && isStringLiteralPart(contained, context);
    }
    else if (element instanceof PyBinaryExpression) {
      final PyBinaryExpression expr = (PyBinaryExpression)element;
      final PyExpression left = expr.getLeftExpression();
      final PyExpression right = expr.getRightExpression();
      if (expr.isOperator("+")) {
        return isStringLiteralPart(left, context) || right != null && isStringLiteralPart(right, context);
      }
      else if (expr.isOperator("%")) {
        return right != context && isStringLiteralPart(left, context);
      }
      return false;
    }
    else if (element instanceof PyCallExpression) {
      final PyExpression qualifier = getFormatCallQualifier((PyCallExpression)element);
      return qualifier != null && isStringLiteralPart(qualifier, context);
    }
    else if (element instanceof PyReferenceExpression) {
      final PyCallExpression callExpr = PyCallExpressionNavigator.getPyCallExpressionByCallee(element);
      return callExpr != null && isStringLiteralPart(callExpr, context);
    }
    return false;
  }

  @Nullable
  private static PyExpression getFormatCallQualifier(@NotNull PyCallExpression element) {
    final PyExpression callee = element.getCallee();
    if (callee instanceof PyQualifiedExpression) {
      final PyQualifiedExpression qualifiedExpr = (PyQualifiedExpression)callee;
      final PyExpression qualifier = qualifiedExpr.getQualifier();
      if (qualifier != null && PyNames.FORMAT.equals(qualifiedExpr.getReferencedName())) {
        return qualifier;
      }
    }
    return null;
  }

  @NotNull
  private static InjectionResult processStringLiteral(@NotNull PsiElement element, @NotNull MultiHostRegistrar registrar,
                                                      @NotNull String prefix, @NotNull String suffix, @NotNull Formatting formatting) {
    final String missingValue = "missing_value";
    if (element instanceof PyStringLiteralExpression) {
      boolean injected = false;
      boolean strict = true;
      final PyStringLiteralExpression expr = (PyStringLiteralExpression)element;
      final List<TextRange> ranges = expr.getStringValueTextRanges();
      final String text = expr.getText();
      for (TextRange range : ranges) {
        if (formatting != Formatting.NONE) {
          final String part = range.substring(text);
          final List<FormatStringChunk> chunks = formatting == Formatting.NEW_STYLE ? parseNewStyleFormat(part) : parsePercentFormat(part);
          if (!filterSubstitutions(chunks).isEmpty()) {
            strict = false;
          }
          for (int i = 0; i < chunks.size(); i++) {
            final FormatStringChunk chunk = chunks.get(i);
            if (chunk instanceof ConstantChunk) {
              final int nextIndex = i + 1;
              final String chunkPrefix;
              if (i == 1 && chunks.get(0) instanceof SubstitutionChunk) {
                chunkPrefix = missingValue;
              }
              else if (i == 0) {
                chunkPrefix = prefix;
              } else {
                chunkPrefix = "";
              }
              final String chunkSuffix;
              if (nextIndex < chunks.size() && chunks.get(nextIndex) instanceof SubstitutionChunk) {
                chunkSuffix = missingValue;
              }
              else if (nextIndex == chunks.size()) {
                chunkSuffix = suffix;
              }
              else {
                chunkSuffix = "";
              }
              final TextRange chunkRange = chunk.getTextRange().shiftRight(range.getStartOffset());
              registrar.addPlace(chunkPrefix, chunkSuffix, expr, chunkRange);
              injected = true;
            }
          }
        }
        else {
          registrar.addPlace(prefix, suffix, expr, range);
          injected = true;
        }
      }
      return new InjectionResult(injected, strict);
    }
    else if (element instanceof PyParenthesizedExpression) {
      final PyExpression contained = ((PyParenthesizedExpression)element).getContainedExpression();
      if (contained != null) {
        return processStringLiteral(contained, registrar, prefix, suffix, formatting);
      }
    }
    else if (element instanceof PyBinaryExpression) {
      final PyBinaryExpression expr = (PyBinaryExpression)element;
      final PyExpression left = expr.getLeftExpression();
      final PyExpression right = expr.getRightExpression();
      final boolean isLeftString = isStringLiteralPart(left, null);
      if (expr.isOperator("+")) {
        final boolean isRightString = right != null && isStringLiteralPart(right, null);
        InjectionResult result = InjectionResult.EMPTY;
        if (isLeftString) {
          result = result.append(processStringLiteral(left, registrar, prefix, isRightString ? "" : missingValue, formatting));
        }
        if (isRightString) {
          result = result.append(processStringLiteral(right, registrar, isLeftString ? "" : missingValue, suffix, formatting));
        }
        return result;
      }
      else if (expr.isOperator("%")) {
        return processStringLiteral(left, registrar, prefix, suffix, Formatting.PERCENT);
      }
    }
    else if (element instanceof PyCallExpression) {
      final PyExpression qualifier = getFormatCallQualifier((PyCallExpression)element);
      if (qualifier != null) {
        return processStringLiteral(qualifier, registrar, prefix, suffix, Formatting.NEW_STYLE);
      }
    }
    return InjectionResult.EMPTY;
  }

  private enum Formatting {
    NONE,
    PERCENT,
    NEW_STYLE
  }
}
