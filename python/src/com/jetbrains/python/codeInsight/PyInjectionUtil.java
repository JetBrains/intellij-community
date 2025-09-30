// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight;

import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyCallExpressionNavigator;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.jetbrains.python.PyStringFormatParser.*;

public final class PyInjectionUtil {

  public static class InjectionResult {
    public static final InjectionResult EMPTY = new InjectionResult(false, true);

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
    List.of(PyStringLiteralExpression.class, PyParenthesizedExpression.class, PyBinaryExpression.class,
            PyCallExpression.class, PsiComment.class);

  private PyInjectionUtil() {}

  /**
   * Returns the largest expression in the specified context that represents a string literal suitable for language injection, possibly
   * with concatenation, parentheses, or formatting.
   */
  public static @Nullable PsiElement getLargestStringLiteral(@NotNull PsiElement context) {
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
  public static @NotNull InjectionResult registerStringLiteralInjection(@NotNull PsiElement element,
                                                                        @NotNull MultiHostRegistrar registrar,
                                                                        @NotNull Language language) {
    return registerStringLiteralInjection(element, registrar, language, "", "");
  }

  /**
   * Registers language injections like {@link #registerStringLiteralInjection} but wraps the entire injected range with parentheses.
   * This is useful when the injected fragment must be implicitly parenthesized like a type annotation string injection.
   */
  public static @NotNull InjectionResult registerStringLiteralInjectionWithParenthesis(@NotNull PsiElement element,
                                                                        @NotNull MultiHostRegistrar registrar,
                                                                        @NotNull Language language) {
    return registerStringLiteralInjection(element, registrar, language, "(", ")");
  }

  private static @NotNull InjectionResult registerStringLiteralInjection(@NotNull PsiElement element,
                                                                        @NotNull MultiHostRegistrar registrar,
                                                                        @NotNull Language language,
                                                                        @NotNull String prefix,
                                                                        @NotNull String suffix) {
    registrar.startInjecting(language);
    final InjectionResult result = processStringLiteral(element, registrar, prefix, suffix, Formatting.NONE);
    if (result.isInjected()) {
      registrar.frankensteinInjection(!result.isStrict())
        .doneInjecting();
    }
    return result;
  }

  private static boolean isStringLiteralPart(@NotNull PsiElement element, @Nullable PsiElement context) {
    if (element == context || element instanceof PyStringLiteralExpression || element instanceof PsiComment) {
      return true;
    }
    else if (element instanceof PyParenthesizedExpression) {
      final PyExpression contained = ((PyParenthesizedExpression)element).getContainedExpression();
      return contained != null && isStringLiteralPart(contained, context);
    }
    else if (element instanceof PyBinaryExpression expr) {
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

  private static @Nullable PyExpression getFormatCallQualifier(@NotNull PyCallExpression element) {
    final PyExpression callee = element.getCallee();
    if (callee instanceof PyQualifiedExpression qualifiedExpr) {
      final PyExpression qualifier = qualifiedExpr.getQualifier();
      if (qualifier != null && PyNames.FORMAT.equals(qualifiedExpr.getReferencedName())) {
        return qualifier;
      }
    }
    return null;
  }

  private static @NotNull InjectionResult processStringLiteral(@NotNull PsiElement element, @NotNull MultiHostRegistrar registrar,
                                                               @NotNull String prefix, @NotNull String suffix, @NotNull Formatting formatting) {
    final String missingValue = "missing_value";
    if (element instanceof PyStringLiteralExpression expr) {
      boolean injected = false;
      boolean strict = true;
      for (PyStringElement stringElem : expr.getStringElements()) {
        final int nodeOffsetInParent = stringElem.getTextOffset() - expr.getTextRange().getStartOffset();
        final TextRange contentRange = stringElem.getContentRange();
        final int contentStartOffset = contentRange.getStartOffset();
        if (formatting != Formatting.NONE || stringElem.isFormatted() || stringElem.isTemplate()) {
          // Each range is relative to the start of the string element
          final List<TextRange> subsRanges;
          if (formatting != Formatting.NONE) {
            final String content = stringElem.getContent();
            subsRanges = StreamEx.of(formatting == Formatting.NEW_STYLE ? parseNewStyleFormat(content) : parsePercentFormat(content))
                                 .select(SubstitutionChunk.class)
                                 .map(chunk -> chunk.getTextRange().shiftRight(contentStartOffset))
                                 .toList();
          }
          else {
            subsRanges = ContainerUtil.map(((PyFormattedStringElement)stringElem).getFragments(), PsiElement::getTextRangeInParent);
          }
          if (!subsRanges.isEmpty()) {
            strict = false;
          }


          final TextRange sentinel = TextRange.from(contentRange.getEndOffset(), 0);
          final List<TextRange> withSentinel = ContainerUtil.append(subsRanges, sentinel);

          int literalChunkStart = contentStartOffset;
          int literalChunkEnd;
          for (int i = 0; i < withSentinel.size(); i++) {
            final TextRange subRange = withSentinel.get(i);
            literalChunkEnd = subRange.getStartOffset();
            if (literalChunkEnd > literalChunkStart) {
              final String chunkPrefix;
              if (i == 0) {
                chunkPrefix = prefix;
              }
              else if (i == 1 && withSentinel.get(0).getStartOffset() == contentStartOffset) {
                chunkPrefix = missingValue;
              }
              else {
                chunkPrefix = "";
              }

              final String chunkSuffix;
              if (i < withSentinel.size() - 1) {
                chunkSuffix = missingValue;
              }
              else if (i == withSentinel.size() - 1) {
                chunkSuffix = suffix;
              }
              else {
                chunkSuffix = "";
              }

              final TextRange chunkRange = TextRange.create(literalChunkStart, literalChunkEnd);
              registrar.addPlace(chunkPrefix, chunkSuffix, expr, chunkRange.shiftRight(nodeOffsetInParent));
              injected = true;
            }
            literalChunkStart = subRange.getEndOffset();
          }
        }
        else {
          registrar.addPlace(prefix, suffix, expr, contentRange.shiftRight(nodeOffsetInParent));
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
    else if (element instanceof PyBinaryExpression expr) {
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
