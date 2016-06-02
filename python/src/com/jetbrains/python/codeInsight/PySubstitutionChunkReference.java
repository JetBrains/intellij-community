/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.google.common.collect.Iterables;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.inspections.PyStringFormatParser;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class PySubstitutionChunkReference extends PsiReferenceBase<PyStringLiteralExpression> implements PsiReferenceEx{
  private final int myPosition;
  @NotNull private final PyStringFormatParser.SubstitutionChunk myChunk;
  private final boolean myIsPercent;

  public PySubstitutionChunkReference(@NotNull final PyStringLiteralExpression element,
                                      @NotNull final PyStringFormatParser.SubstitutionChunk chunk, final int position, boolean isPercent) {
    super(element, getKeywordRange(element, chunk));
    myChunk = chunk;
    myPosition = position;
    myIsPercent = isPercent;
  }
  
  @Nullable
  @Override
  public HighlightSeverity getUnresolvedHighlightSeverity(@NotNull final TypeEvalContext context) {
    return HighlightSeverity.WARNING;
  }

  @Nullable
  @Override
  public String getUnresolvedDescription() {
    return null;
  }

  @NotNull
  private static TextRange getKeywordRange(@NotNull final PyStringLiteralExpression element,
                                           @NotNull final PyStringFormatParser.SubstitutionChunk chunk) {
    final TextRange textRange = chunk.getTextRange();
    if (chunk.getMappingKey() != null) {
      final int start = textRange.getStartOffset() + chunk.getTextRange().substring(element.getText()).indexOf(chunk.getMappingKey());
      return new TextRange(start, start + chunk.getMappingKey().length());
    }
    return textRange;
  }

  @Nullable
  @Override
  public PsiElement resolve() {
    return myIsPercent ? resolvePercentString() : resolveFormatString();
  }

  @Nullable
  private PsiElement resolveFormatString() {
    final PyArgumentList argumentList = getArgumentList(getElement());
    if (argumentList == null || argumentList.getArguments().length == 0) {
      return null;
    }
    return myChunk.getMappingKey() != null ? resolveKeywordFormat(argumentList) : resolvePositionalFormat(argumentList);
  }

  @Nullable
  private PsiElement resolvePositionalFormat(@NotNull PyArgumentList argumentList) {
    final int position = myChunk.getPosition() == null ? myPosition : myChunk.getPosition();
    int n = 0;
    PyStarArgument firstStarArg = null;
    for (PyExpression arg : argumentList.getArguments()) {
      final PyStarArgument starArg = PyUtil.as(arg, PyStarArgument.class);
      if (starArg != null) {
        if (!starArg.isKeyword()) {
          if (firstStarArg == null) {
            firstStarArg = starArg;
          }
          // TODO: Support multiple *args for Python 3.5+
          final PsiElement resolved = resolvePositionalStarExpression(starArg, n);
          if (resolved != null) {
            return resolved;
          }
        }
      }
      else if (!(arg instanceof PyKeywordArgument)) {
        if (position == n) {
          return arg;
        }
        n++;
      }
    }
    return firstStarArg;
  }

  @Nullable
  private PsiElement resolveKeywordFormat(@NotNull PyArgumentList argumentList) {
    final PyKeywordArgument keywordResult = argumentList.getKeywordArgument(myChunk.getMappingKey());
    if (keywordResult != null) {
      return keywordResult;
    }
    else {
      final List<PyStarArgument> keywordStarArgs = getStarArguments(argumentList, true);
      boolean notSureAboutStarArgs = false;
      for (PyStarArgument arg : keywordStarArgs) {
        final Ref<PsiElement> resolvedRef = resolveKeywordStarExpression(arg);
        if (resolvedRef != null) {
          final PsiElement resolved = resolvedRef.get();
          if (resolved != null) {
            return resolved;
          }
        }
        else {
          notSureAboutStarArgs = true;
        }
      }
      return notSureAboutStarArgs ? Iterables.getFirst(keywordStarArgs, null) : null;
    }
  }

  @NotNull
  private static List<PyStarArgument> getStarArguments(@NotNull PyArgumentList argumentList, boolean isKeyword) {
    return Arrays.asList(argumentList.getArguments()).stream()
      .map(expression -> PyUtil.as(expression, PyStarArgument.class))
      .filter(argument -> argument != null && argument.isKeyword() == isKeyword).collect(Collectors.toList());
  }

  @Nullable
  private PsiElement resolvePercentString() {
    final PyBinaryExpression binaryExpression = PsiTreeUtil.getParentOfType(getElement(), PyBinaryExpression.class);
    if (binaryExpression != null) {
      final PyExpression rightExpression = binaryExpression.getRightExpression();
      if (rightExpression == null) {
        return null;
      }
      boolean isKeyWordSubstitution = myChunk.getMappingKey() != null;
      return isKeyWordSubstitution ? resolveKeywordPercent(rightExpression) : resolvePositionalPercent(rightExpression);
    }
    return null;
  }

  @Nullable
  private PsiElement resolveKeywordPercent(@NotNull PyExpression expression) {
    final PyExpression containedExpr = PyPsiUtils.flattenParens(expression);
    if (containedExpr instanceof PyDictLiteralExpression) {
      final Ref<PsiElement> resolvedRef = resolveDictLiteralExpression((PyDictLiteralExpression)containedExpr);
      return resolvedRef != null ? resolvedRef.get() : containedExpr;
    }
    else if (PyUtil.instanceOf(containedExpr, PyLiteralExpression.class, PySetLiteralExpression.class,
                               PyListLiteralExpression.class, PyTupleExpression.class)) {
      return null;
    }
    else if (containedExpr instanceof PyCallExpression) {
      return resolveDictCall((PyCallExpression)containedExpr);
    }
    return containedExpr;
  }

  @Nullable
  private PsiElement resolvePositionalPercent(@NotNull PyExpression expression) {
    final PyExpression containedExpression = PyPsiUtils.flattenParens(expression);
    if (containedExpression instanceof PyTupleExpression) {
      final PyExpression[] elements = ((PyTupleExpression)containedExpression).getElements();
      return myPosition < elements.length ? elements[myPosition] : null;
    }
    else if (containedExpression instanceof PyBinaryExpression && ((PyBinaryExpression)containedExpression).isOperator("+")) {
      return resolveNotNestedBinaryExpression((PyBinaryExpression)containedExpression);
    }
    else if (containedExpression instanceof PyCallExpression) {
      final PyExpression callee = ((PyCallExpression)containedExpression).getCallee();
      if (callee != null && "dict".equals(callee.getName()) && myPosition != 0) {
        return null;
      }
    }
    else if (myPosition != 0 && PsiTreeUtil.instanceOf(containedExpression, PyLiteralExpression.class, PySetLiteralExpression.class,
                                                       PyListLiteralExpression.class, PyDictLiteralExpression.class)) {
      return null;
    }
    return containedExpression;
  }

  @Nullable
  private PsiElement resolveNotNestedBinaryExpression(PyBinaryExpression containedExpression) {
    PyExpression left = containedExpression.getLeftExpression();
    PyExpression right = containedExpression.getRightExpression();
    if (left instanceof PyParenthesizedExpression) {
      PyExpression leftTuple = PyPsiUtils.flattenParens(left);
      if (leftTuple instanceof PyTupleExpression) {
        PyExpression[] leftTupleElements = ((PyTupleExpression)leftTuple).getElements();
        int leftTupleLength = leftTupleElements.length;
        if (leftTupleLength > myPosition) {
          return leftTupleElements[myPosition];
        }
        if (right instanceof PyParenthesizedExpression) {
          PyExpression rightTuple = PyPsiUtils.flattenParens(right);
          if (rightTuple instanceof PyTupleExpression) {
            PyExpression[] rightTupleElements = ((PyTupleExpression)rightTuple).getElements();
            int rightLength = rightTupleElements.length;
            if (leftTupleLength + rightLength > myPosition)
              return rightTupleElements[myPosition - leftTupleLength];
          }
        }
      }
    }
    return containedExpression;
  }

  @Nullable
  private static PyArgumentList getArgumentList(final PsiElement original) {
    final PsiElement pyReferenceExpression = PsiTreeUtil.getParentOfType(original, PyReferenceExpression.class);
    return PsiTreeUtil.getNextSiblingOfType(pyReferenceExpression, PyArgumentList.class);
  }

  @Nullable
  private Ref<PsiElement> resolveKeywordStarExpression(@NotNull PyStarArgument starArgument) {
    final PyDictLiteralExpression dictExpr = PsiTreeUtil.getChildOfType(starArgument, PyDictLiteralExpression.class);
    return dictExpr != null ? resolveDictLiteralExpression(dictExpr) : null;
  }
  
  @Nullable
  private PsiElement resolvePositionalStarExpression(@NotNull PyStarArgument starArgument, int argumentPosition) {
    final PyExpression expr = PsiTreeUtil.getChildOfAnyType(starArgument, PyListLiteralExpression.class, PyParenthesizedExpression.class);
    if (expr == null) {
      return starArgument;
    }
    final int position = (myChunk.getPosition() != null ? myChunk.getPosition() : myPosition) - argumentPosition;
    final PyExpression[] elements;
    if (expr instanceof PyListLiteralExpression) {
      elements = ((PyListLiteralExpression)expr).getElements();
    }
    else if (expr instanceof PyParenthesizedExpression) {
      final PyExpression expression = PyPsiUtils.flattenParens(expr);
      final PyTupleExpression tupleExpr = PyUtil.as(expression, PyTupleExpression.class);
      if (tupleExpr == null) {
        return starArgument;
      }
      elements = tupleExpr.getElements();
    }
    else {
      elements = null;
    }
    return elements != null && position < elements.length ? elements[position] : null;
  }

  @Nullable
  private Ref<PsiElement> resolveDictLiteralExpression(PyDictLiteralExpression expression) {
    final PyKeyValueExpression[] keyValueExpressions = expression.getElements();
    if (keyValueExpressions.length == 0) {
      return Ref.create();
    }
    boolean allKeysForSure = true;
    for (PyKeyValueExpression keyValueExpression : keyValueExpressions) {
      PyExpression keyExpression = keyValueExpression.getKey();
      if (keyExpression instanceof PyStringLiteralExpression) {
        final PyStringLiteralExpression key = (PyStringLiteralExpression)keyExpression;
        if (key.getStringValue().equals(myChunk.getMappingKey())) {
          return Ref.create(key);
        }
      }
      else if (!(keyExpression instanceof PyLiteralExpression)) {
        allKeysForSure = false;
      }
    }
    return allKeysForSure ? Ref.create() : null;
  }

  @Nullable
  private PsiElement resolveDictCall(@NotNull PyCallExpression expression) {
    final PyExpression callee = expression.getCallee();
    if (callee != null) {
      final String name = callee.getName();
      if ("dict".equals(name)) {
        for (PyExpression arg : expression.getArguments()) {
          if (!(arg instanceof PyKeywordArgument)) {
            return expression;
          }
        }
        final PyArgumentList argumentList = expression.getArgumentList();
        if (argumentList != null) {
          return argumentList.getKeywordArgument(myChunk.getMappingKey());
        }
      }
    }
    return expression;
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }
}
