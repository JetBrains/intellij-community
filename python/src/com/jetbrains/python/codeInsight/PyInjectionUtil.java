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

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.fstrings.FStringParser;
import com.jetbrains.python.codeInsight.fstrings.PyFStringsInjector;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyCallExpressionNavigator;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.jetbrains.python.inspections.PyStringFormatParser.*;

/**
 * @author vlan
 */
public class PyInjectionUtil {

  public static class InjectionResult {
    public static final InjectionResult EMPTY = new InjectionResult(false, true, Collections.emptyList());

    private final boolean myInjected;
    private final boolean myStrict;
    private final List<PyStringLiteralExpression> myCollectedFStrings;

    public InjectionResult(boolean injected, boolean strict) {this(injected, strict, Collections.emptyList());}

    private InjectionResult(boolean injected, boolean strict, @NotNull List<PyStringLiteralExpression> nodes) {
      myInjected = injected;
      myStrict = strict;
      myCollectedFStrings = nodes;
    }

    public boolean isInjected() {
      return myInjected;
    }

    public boolean isStrict() {
      return myStrict;
    }

    public InjectionResult append(@NotNull InjectionResult result) {
      return new InjectionResult(myInjected || result.isInjected(),
                                 myStrict && result.isStrict(),
                                 ContainerUtil.concat(myCollectedFStrings, result.myCollectedFStrings));
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
  public static InjectionResult registerStringLiteralInjection(@NotNull PsiElement element,
                                                               @NotNull MultiHostRegistrar registrar,
                                                               @NotNull Language language) {
    registrar.startInjecting(language);
    final InjectionResult result = processStringLiteral(element, registrar, "", "", Formatting.NONE);
    if (result.isInjected()) {
      registrar.doneInjecting();
    }

    // Only one injector can process the given element, thus we should additionally
    // take care of f-string here instead of PyFStringsInjector
    for (PyStringLiteralExpression literal: result.myCollectedFStrings) {
      PyFStringsInjector.injectFStringFragments(registrar, literal);
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
      boolean hasFormattedNodes = false;
      for (ASTNode node : expr.getStringNodes()) {
        final int nodeOffsetInParent = node.getStartOffset() - expr.getTextRange().getStartOffset();
        final PyUtil.StringNodeInfo nodeInfo = new PyUtil.StringNodeInfo(node);
        final TextRange contentRange = nodeInfo.getContentRange();
        final int contentStartOffset = contentRange.getStartOffset();
        if (formatting != Formatting.NONE || nodeInfo.isFormatted()) {
          // Each range is relative to the start of the string node
          final List<TextRange> subsRanges;
          if (formatting != Formatting.NONE) {
            final String content = nodeInfo.getContent();
            subsRanges = StreamEx.of(formatting == Formatting.NEW_STYLE ? parseNewStyleFormat(content) : parsePercentFormat(content))
                                 .select(SubstitutionChunk.class)
                                 .map(chunk -> chunk.getTextRange().shiftRight(contentStartOffset))
                                 .toList();
          }
          else {
            hasFormattedNodes = true;
            // f-string fragment parser handles string literal prefix and opening quotes itself
            subsRanges = StreamEx.of(FStringParser.parse(node.getText()).getFragments())
                                 .filter(f -> f.getDepth() == 1) // don't consider nested fragments like {foo:{bar}}
                                 .map(f -> TextRange.create(f.getLeftBraceOffset(),
                                                            Math.max(f.getRightBraceOffset() + 1, f.getContentEndOffset())))
                                 .toList();
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
      return new InjectionResult(injected, strict, hasFormattedNodes ? Collections.singletonList(expr) : Collections.emptyList());
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
