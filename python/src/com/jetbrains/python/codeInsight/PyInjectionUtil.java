/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

import static com.jetbrains.python.inspections.PyStringFormatParser.*;

/**
 * @author vlan
 */
public class PyInjectionUtil {
  public static final List<Class<? extends PyExpression>> ELEMENTS_TO_INJECT_IN =
    Arrays.asList(PyStringLiteralExpression.class, PyParenthesizedExpression.class, PyBinaryExpression.class, PyCallExpression.class);

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
  public static void registerStringLiteralInjection(@NotNull PsiElement element, @NotNull MultiHostRegistrar registrar) {
    processStringLiteral(element, registrar, "", "", Formatting.PERCENT);
  }

  private static boolean isStringLiteralPart(@NotNull PsiElement element, @Nullable PsiElement context) {
    if (element == context) {
      return true;
    }
    else if (element instanceof PyStringLiteralExpression) {
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

  private static void processStringLiteral(@NotNull PsiElement element, @NotNull MultiHostRegistrar registrar, @NotNull String prefix,
                                           @NotNull String suffix, @NotNull Formatting formatting) {
    final String missingValue = "missing";
    if (element instanceof PyStringLiteralExpression) {
      final PyStringLiteralExpression expr = (PyStringLiteralExpression)element;
      final List<TextRange> ranges = expr.getStringValueTextRanges();
      final String text = expr.getText();
      for (TextRange range : ranges) {
        if (formatting != Formatting.NONE) {
          final String part = range.substring(text);
          final List<FormatStringChunk> chunks = formatting == Formatting.NEW_STYLE ? parseNewStyleFormat(part) : parsePercentFormat(part);
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
            }
          }
        }
        else {
          registrar.addPlace(prefix, suffix, expr, range);
        }
      }
    }
    else if (element instanceof PyParenthesizedExpression) {
      final PyExpression contained = ((PyParenthesizedExpression)element).getContainedExpression();
      if (contained != null) {
        processStringLiteral(contained, registrar, prefix, suffix, formatting);
      }
    }
    else if (element instanceof PyBinaryExpression) {
      final PyBinaryExpression expr = (PyBinaryExpression)element;
      final PyExpression left = expr.getLeftExpression();
      final PyExpression right = expr.getRightExpression();
      final boolean isLeftString = isStringLiteralPart(left, null);
      if (expr.isOperator("+")) {
        final boolean isRightString = right != null && isStringLiteralPart(right, null);
        if (isLeftString) {
          processStringLiteral(left, registrar, prefix, isRightString ? "" : missingValue, formatting);
        }
        if (isRightString) {
          processStringLiteral(right, registrar, isLeftString ? "" : missingValue, suffix, formatting);
        }
      }
      else if (expr.isOperator("%")) {
        processStringLiteral(left, registrar, prefix, suffix, Formatting.PERCENT);
      }
    }
    else if (element instanceof PyCallExpression) {
      final PyExpression qualifier = getFormatCallQualifier((PyCallExpression)element);
      if (qualifier != null) {
        processStringLiteral(qualifier, registrar, prefix, suffix, Formatting.NEW_STYLE);
      }
    }
  }

  private enum Formatting {
    NONE,
    PERCENT,
    NEW_STYLE
  }
}
