/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.refactoring;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeChecker;
import com.jetbrains.python.psi.types.PyTypeParser;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.jetbrains.python.PyTokenTypes.*;
import static com.jetbrains.python.inspections.PyStringFormatParser.*;

/**
 * @author Dennis.Ushakov
 */
public class PyReplaceExpressionUtil implements PyElementTypes {
  /**
   * This marker is added in cases where valid selection nevertheless breaks existing expression.
   * It can happen in cases like (here {@code <start> and <end>} represent selection boundaries):
   * <ul>
   *   <li>Selection conflicts with operator precedence: {@code n = 1 * <start>2 + 3<end>}</li>
   *   <li>Selection conflicts with operator associativity: {@code n = 1 + <start>2 + 3<end>}</li>
   *   <li>Part of string literal is selected: {@code s = 'green <start>eggs<end> and ham'}</li>
   * </ul>
   */
  public static final Key<Pair<PsiElement, TextRange>> SELECTION_BREAKS_AST_NODE =
    new Key<>("python.selection.breaks.ast.node");

  private PyReplaceExpressionUtil() {}

  /**
   * @param oldExpr old expression that will be substituted
   * @param newExpr new expression to substitute with
   * @return whether new expression should be wrapped in parenthesis to preserve original semantics
   */
  public static boolean isNeedParenthesis(@NotNull final PyElement oldExpr, @NotNull final PyElement newExpr) {
    final PyElement parentExpr = (PyElement)oldExpr.getParent();
    if (parentExpr instanceof PyArgumentList) {
      return newExpr instanceof PyTupleExpression;
    }
    if (parentExpr instanceof PyParenthesizedExpression || !(parentExpr instanceof PyExpression)) {
      return false;
    }
    final int newPriority = getExpressionPriority(newExpr);
    final int parentPriority = getExpressionPriority(parentExpr);
    if (parentPriority > newPriority) {
      return true;
    }
    else if (parentPriority == newPriority && parentPriority != 0 && parentExpr instanceof PyBinaryExpression) {
      final PyBinaryExpression binaryExpression = (PyBinaryExpression)parentExpr;
      if (isNotAssociative(binaryExpression) && oldExpr == getLeastPrioritySide(binaryExpression)) {
        return true;
      }
    }
    else if (newExpr instanceof PyConditionalExpression && parentExpr instanceof PyConditionalExpression) {
      return true;
    }
    return false;
  }

  @Nullable
  private static PyExpression getLeastPrioritySide(@NotNull PyBinaryExpression expression) {
    if (expression.isOperator("**")) {
      return expression.getLeftExpression();
    }
    else {
      return expression.getRightExpression();
    }
  }

  public static PsiElement replaceExpression(@NotNull final PsiElement oldExpression,
                                             @NotNull final PsiElement newExpression) {
    final Pair<PsiElement, TextRange> data = oldExpression.getUserData(SELECTION_BREAKS_AST_NODE);
    if (data != null) {
      final PsiElement element = data.first;
      final TextRange textRange = data.second;
      final String parentText = element.getText();
      final String prefix = parentText.substring(0, textRange.getStartOffset());
      final String suffix = parentText.substring(textRange.getEndOffset(), element.getTextLength());
      final PyElementGenerator generator = PyElementGenerator.getInstance(oldExpression.getProject());
      final LanguageLevel languageLevel = LanguageLevel.forElement(oldExpression);
      if (element instanceof PyStringLiteralExpression) {
        return replaceSubstringInStringLiteral((PyStringLiteralExpression)element, newExpression, textRange);
      }
      final PyElement newElement = generator.createFromText(languageLevel, PyElement.class, prefix + newExpression.getText() + suffix);
      return element.replace(newElement);
    }
    else {
      return oldExpression.replace(newExpression);
    }
  }

  @Nullable
  private static PsiElement replaceSubstringInStringLiteral(@NotNull PyStringLiteralExpression oldExpression,
                                                            @NotNull PsiElement newExpression,
                                                            @NotNull TextRange textRange) {
    final String fullText = oldExpression.getText();
    final Pair<String, String> detectedQuotes = PyStringLiteralUtil.getQuotes(fullText);
    final Pair<String, String> quotes = detectedQuotes != null ? detectedQuotes : Pair.create("'", "'");
    final String prefix = fullText.substring(0, textRange.getStartOffset());
    final String suffix = fullText.substring(textRange.getEndOffset(), oldExpression.getTextLength());
    final PyExpression formatValue = getFormatValueExpression(oldExpression);
    final PyArgumentList newStyleFormatValue = getNewStyleFormatValueExpression(oldExpression);
    final String newText = newExpression.getText();

    final List<SubstitutionChunk> substitutions;
    if (newStyleFormatValue != null) {
      substitutions = filterSubstitutions(parseNewStyleFormat(fullText));
    }
    else {
      substitutions = filterSubstitutions(parsePercentFormat(fullText));
    }
    final boolean hasSubstitutions = substitutions.size() > 0;

    if (formatValue != null && !containsStringFormatting(substitutions, textRange)) {
      if (formatValue instanceof PyTupleExpression) {
        return replaceSubstringWithTupleFormatting(oldExpression, newExpression, textRange, prefix, suffix,
                                                   (PyTupleExpression)formatValue, substitutions);
      }
      else if (formatValue instanceof PyDictLiteralExpression) {
        return replaceSubstringWithDictFormatting(oldExpression, quotes, prefix, suffix, formatValue, newText);
      }
      else {
        final TypeEvalContext context = TypeEvalContext.userInitiated(oldExpression.getProject(), oldExpression.getContainingFile());
        final PyType valueType = context.getType(formatValue);
        final PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(oldExpression);
        final PyType tupleType = builtinCache.getTupleType();
        final PyType mappingType = PyTypeParser.getTypeByName(null, "collections.Mapping", context);
        if (!PyTypeChecker.match(tupleType, valueType, context) ||
            (mappingType != null && !PyTypeChecker.match(mappingType, valueType, context))) {
          return replaceSubstringWithSingleValueFormatting(oldExpression, textRange, prefix, suffix, formatValue, newText, substitutions);
        }
      }
    }

    if (newStyleFormatValue != null && hasSubstitutions && !containsStringFormatting(substitutions, textRange)) {
      final PyExpression[] arguments = newStyleFormatValue.getArguments();
      boolean hasStarArguments = false;
      for (PyExpression argument : arguments) {
        if (argument instanceof PyStarArgument) {
          hasStarArguments = true;
        }
      }
      if (!hasStarArguments) {
        return replaceSubstringWithNewStyleFormatting(oldExpression, textRange, prefix, suffix, newStyleFormatValue, newText,
                                                      substitutions);
      }
    }

    if (isConcatFormatting(oldExpression) || hasSubstitutions) {
      return replaceSubstringWithConcatFormatting(oldExpression, quotes, prefix, suffix, newText, hasSubstitutions);
    }

    return replaceSubstringWithoutFormatting(oldExpression, prefix, suffix, newText);
  }

  private static PsiElement replaceSubstringWithSingleValueFormatting(PyStringLiteralExpression oldExpression,
                                                                      TextRange textRange,
                                                                      String prefix,
                                                                      String suffix,
                                                                      PyExpression formatValue,
                                                                      String newText,
                                                                      List<SubstitutionChunk> substitutions) {
    // 'foo%s' % value if value is not tuple or mapping -> '%s%s' % (s, value)
    final PyElementGenerator generator = PyElementGenerator.getInstance(oldExpression.getProject());
    final LanguageLevel languageLevel = LanguageLevel.forElement(oldExpression);
    final String newLiteralText = prefix + "%s" + suffix;
    final PyStringLiteralExpression newLiteralExpression = generator.createStringLiteralAlreadyEscaped(newLiteralText);
    oldExpression.replace(newLiteralExpression);
    final StringBuilder builder = new StringBuilder();
    builder.append("(");
    final int i = getPositionInRanges(substitutionsToRanges(substitutions), textRange);
    final int pos;
    if (i == 0) {
      pos = builder.toString().length();
      builder.append(newText);
      builder.append(",");
      builder.append(formatValue.getText());
    }
    else {
      builder.append(formatValue.getText());
      builder.append(",");
      pos = builder.toString().length();
      builder.append(newText);
    }
    builder.append(")");
    final PsiElement newElement = formatValue.replace(generator.createExpressionFromText(languageLevel, builder.toString()));
    return newElement.findElementAt(pos);
  }

  private static PsiElement replaceSubstringWithDictFormatting(PyStringLiteralExpression oldExpression,
                                                               Pair<String, String> quotes,
                                                               String prefix,
                                                               String suffix,
                                                               PyExpression formatValue,
                                                               String newText) {
    // 'foo%(x)s' % {'x': x} -> '%(s)s%(x)s' % {'x': x, 's': s}
    // TODO: Support the dict() function
    final PyElementGenerator generator = PyElementGenerator.getInstance(oldExpression.getProject());
    final LanguageLevel languageLevel = LanguageLevel.forElement(oldExpression);
    final String newLiteralText = prefix + "%(" + newText + ")s" + suffix;
    final PyStringLiteralExpression newLiteralExpression = generator.createStringLiteralAlreadyEscaped(newLiteralText);
    oldExpression.replace(newLiteralExpression);

    final PyDictLiteralExpression dict = (PyDictLiteralExpression)formatValue;
    final StringBuilder builder = new StringBuilder();
    builder.append("{");
    final PyKeyValueExpression[] elements = dict.getElements();
    builder.append(StringUtil.join(elements, expression -> expression.getText(), ","));
    if (elements.length > 0) {
      builder.append(",");
    }
    builder.append(quotes.getSecond());
    builder.append(newText);
    builder.append(quotes.getSecond());
    builder.append(":");
    final int pos = builder.toString().length();
    builder.append(newText);
    builder.append("}");
    final PyExpression newDictLiteral = generator.createExpressionFromText(languageLevel, builder.toString());
    final PsiElement newElement = formatValue.replace(newDictLiteral);
    return newElement.findElementAt(pos);
  }

  private static PsiElement replaceSubstringWithTupleFormatting(PyStringLiteralExpression oldExpression,
                                                                PsiElement newExpression,
                                                                TextRange textRange,
                                                                String prefix,
                                                                String suffix,
                                                                PyTupleExpression tupleFormatValue,
                                                                List<SubstitutionChunk> substitutions) {
    // 'foo%s' % (x,) -> '%s%s' % (s, x)
    final String newLiteralText = prefix + "%s" + suffix;
    final PyElementGenerator generator = PyElementGenerator.getInstance(oldExpression.getProject());
    final PyStringLiteralExpression newLiteralExpression = generator.createStringLiteralAlreadyEscaped(newLiteralText);
    oldExpression.replace(newLiteralExpression);

    final PyExpression[] members = tupleFormatValue.getElements();
    final int n = members.length;
    final int i = Math.min(n, Math.max(0, getPositionInRanges(substitutionsToRanges(substitutions), textRange)));
    final boolean last = i == n;
    final PsiElement trailingComma = PyPsiUtils.getNextComma(members[n - 1]);
    if (trailingComma != null) {
      trailingComma.delete();
    }
    final PyExpression before = last ? null : members[i];
    PyUtil.addListNode(tupleFormatValue, newExpression, before != null ? before.getNode() : null, i == 0 || !last, last, !last);
    return newExpression;
  }

  private static PsiElement replaceSubstringWithoutFormatting(@NotNull PyStringLiteralExpression oldExpression,
                                                              @NotNull String prefix,
                                                              @NotNull String suffix,
                                                              @NotNull String newText) {
    // 'foobar' -> '%sbar' % s
    final PyElementGenerator generator = PyElementGenerator.getInstance(oldExpression.getProject());
    final LanguageLevel languageLevel = LanguageLevel.forElement(oldExpression);
    final PsiElement parent = oldExpression.getParent();
    final boolean parensNeeded = parent instanceof PyExpression && !(parent instanceof PyParenthesizedExpression);
    final StringBuilder builder = new StringBuilder();
    if (parensNeeded) {
      builder.append("(");
    }
    builder.append(prefix);
    builder.append("%s");
    builder.append(suffix);
    builder.append(" % ");
    final int pos = builder.toString().length();
    builder.append(newText);
    if (parensNeeded) {
      builder.append(")");
    }
    final PyExpression expression = generator.createExpressionFromText(languageLevel, builder.toString());
    final PsiElement newElement = oldExpression.replace(expression);
    return newElement.findElementAt(pos);
  }

  private static PsiElement replaceSubstringWithConcatFormatting(@NotNull PyStringLiteralExpression oldExpression,
                                                                 @NotNull Pair<String, String> quotes,
                                                                 @NotNull String prefix,
                                                                 @NotNull String suffix,
                                                                 @NotNull String newText,
                                                                 boolean hasSubstitutions) {
    // 'foobar' + 'baz' -> s + 'bar' + 'baz'
    // 'foobar%s' -> s + 'bar%s'
    // 'f%soobar' % x -> (s + 'bar') % x
    final PyElementGenerator generator = PyElementGenerator.getInstance(oldExpression.getProject());
    final LanguageLevel languageLevel = LanguageLevel.forElement(oldExpression);
    final String leftQuote = quotes.getFirst();
    final String rightQuote = quotes.getSecond();
    final StringBuilder builder = new StringBuilder();
    if (hasSubstitutions) {
      builder.append("(");
    }
    if (!leftQuote.endsWith(prefix)) {
      builder.append(prefix).append(rightQuote).append(" + ");
    }
    final int pos = builder.toString().length();
    builder.append(newText);
    if (!rightQuote.startsWith(suffix)) {
      builder.append(" + ").append(leftQuote).append(suffix);
    }
    if (hasSubstitutions) {
      builder.append(")");
    }
    final PsiElement expression = generator.createExpressionFromText(languageLevel, builder.toString());
    final PsiElement newElement = oldExpression.replace(expression);
    return newElement.findElementAt(pos);
  }

  private static PsiElement replaceSubstringWithNewStyleFormatting(@NotNull PyStringLiteralExpression oldExpression,
                                                                   @NotNull TextRange textRange,
                                                                   @NotNull String prefix,
                                                                   @NotNull String suffix,
                                                                   @NotNull PyArgumentList newStyleFormatValue,
                                                                   @NotNull String newText,
                                                                   @NotNull List<SubstitutionChunk> substitutions) {
    final PyElementGenerator generator = PyElementGenerator.getInstance(oldExpression.getProject());
    final LanguageLevel languageLevel = LanguageLevel.forElement(oldExpression);
    final PyExpression[] arguments = newStyleFormatValue.getArguments();
    boolean hasKeywords = false;
    int maxPosition = -1;
    for (SubstitutionChunk substitution : substitutions) {
      if (substitution.getMappingKey() != null) {
        hasKeywords = true;
      }
      final Integer position = substitution.getPosition();
      if (position != null && position > maxPosition) {
        maxPosition = position;
      }
    }
    if (hasKeywords) {
      // 'foo{x}'.format(x='bar') -> '{s}oo{x}'.format(x='bar', s=s)
      final String newLiteralText = prefix + "{" + newText + "}" + suffix;
      final PyStringLiteralExpression newLiteralExpression = generator.createStringLiteralAlreadyEscaped(newLiteralText);
      oldExpression.replace(newLiteralExpression);

      final PyKeywordArgument kwarg = generator.createKeywordArgument(languageLevel, newText, newText);
      newStyleFormatValue.addArgument(kwarg);
      return kwarg.getValueExpression();
    }
    else if (maxPosition >= 0) {
      // 'foo{0}'.format('bar') -> '{1}oo{0}'.format('bar', s)
      final String newLiteralText = prefix + "{" + (maxPosition + 1) + "}" + suffix;
      final PyStringLiteralExpression newLiteralExpression = generator.createStringLiteralAlreadyEscaped(newLiteralText);
      oldExpression.replace(newLiteralExpression);

      final PyExpression arg = generator.createExpressionFromText(languageLevel, newText);
      newStyleFormatValue.addArgument(arg);
      return arg;
    }
    else {
      // 'foo{}'.format('bar') -> '{}oo{}'.format(s, 'bar')
      final String newLiteralText = prefix + "{}" + suffix;
      final PyStringLiteralExpression newLiteralExpression = generator.createStringLiteralAlreadyEscaped(newLiteralText);
      oldExpression.replace(newLiteralExpression);
      final int i = getPositionInRanges(substitutionsToRanges(substitutions), textRange);
      final PyExpression arg = generator.createExpressionFromText(languageLevel, newText);
      if (i == 0) {
        newStyleFormatValue.addArgumentFirst(arg);
      }
      else if (i < arguments.length) {
        newStyleFormatValue.addArgumentAfter(arg, arguments[i - 1]);
      }
      else {
        newStyleFormatValue.addArgument(arg);
      }
      return arg;
    }
  }

  private static int getPositionInRanges(@NotNull List<TextRange> ranges, @NotNull TextRange range) {
    final int end = range.getEndOffset();
    final int size = ranges.size();
    for (int i = 0; i < size; i++) {
      final TextRange r = ranges.get(i);
      if (end < r.getStartOffset()) {
        return i;
      }
    }
    return size;
  }

  private static boolean containsStringFormatting(@NotNull List<SubstitutionChunk> substitutions,
                                                  @NotNull TextRange range) {
    final List<TextRange> ranges = substitutionsToRanges(substitutions);
    for (TextRange r : ranges) {
      if (range.contains(r)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isConcatFormatting(PyStringLiteralExpression element) {
    final PsiElement parent = element.getParent();
    return parent instanceof PyBinaryExpression && ((PyBinaryExpression)parent).isOperator("+");
  }

  private static boolean isNotAssociative(@NotNull final PyBinaryExpression binaryExpression) {
    final IElementType opType = getOperationType(binaryExpression);
    return COMPARISON_OPERATIONS.contains(opType) || binaryExpression instanceof PySliceExpression ||
           opType == DIV || opType == FLOORDIV || opType == PERC || opType == EXP || opType == MINUS;
  }

  private static int getExpressionPriority(PyElement expr) {
    int priority = 0;
    if (expr instanceof PyReferenceExpression ||
        expr instanceof PySubscriptionExpression ||
        expr instanceof PySliceExpression ||
        expr instanceof PyCallExpression) priority = 1;
    else if (expr instanceof PyPrefixExpression) {
      final IElementType opType = getOperationType(expr);
      if (opType == PLUS || opType == MINUS || opType == TILDE) priority = 2;
      if (opType == NOT_KEYWORD) priority = 11;
    }
    else if (expr instanceof PyBinaryExpression) {
      final IElementType opType = getOperationType(expr);
      if (opType == EXP) priority =  3;
      if (opType == MULT || opType == AT || opType == DIV || opType == PERC || opType == FLOORDIV) priority =  4;
      if (opType == PLUS || opType == MINUS) priority =  5;
      if (opType == LTLT || opType == GTGT) priority = 6;
      if (opType == AND) priority = 7;
      if (opType == XOR) priority = 8;
      if (opType == OR) priority = 9;
      if (COMPARISON_OPERATIONS.contains(opType)) priority = 10;
      if (opType == AND_KEYWORD) priority = 12;
      if (opType == OR_KEYWORD) priority = 13;
    }
    else if (expr instanceof PyConditionalExpression) priority = 14;
    else if (expr instanceof PyLambdaExpression) priority = 15;

    return -priority;
  }

  @Nullable
  private static IElementType getOperationType(@NotNull final PyElement expr) {
    if (expr instanceof PyBinaryExpression) return ((PyBinaryExpression)expr).getOperator();
    return ((PyPrefixExpression)expr).getOperator();
  }
}
