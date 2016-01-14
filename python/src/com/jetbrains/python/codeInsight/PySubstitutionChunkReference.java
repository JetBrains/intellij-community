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

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.inspections.PyStringFormatParser;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PySubstitutionChunkReference extends PsiReferenceBase<PyStringLiteralExpression> implements PsiReferenceEx{
  private final int myPosition;
  private final PyStringFormatParser.SubstitutionChunk myChunk;

  public PySubstitutionChunkReference(@NotNull final PyStringLiteralExpression element,
                                      @NotNull final PyStringFormatParser.SubstitutionChunk chunk, final int position) {
    super(element, getKeyWordRange(element, chunk));
    myChunk = chunk;
    myPosition = position;
  }
  @Nullable
  @Override
  public HighlightSeverity getUnresolvedHighlightSeverity(@NotNull final TypeEvalContext context) {
    return HighlightSeverity.INFORMATION;
  }

  @Nullable
  @Override
  public String getUnresolvedDescription() {
    return null;
  }

  private static TextRange getKeyWordRange(@NotNull final PyStringLiteralExpression element,
                                           @NotNull final PyStringFormatParser.SubstitutionChunk chunk) {
    final TextRange shifted = chunk.getTextRange().shiftRight(1);
    if (chunk.getMappingKey() != null) {
      final int start = shifted.getStartOffset() + chunk.getTextRange().substring(element.getStringValue()).indexOf(chunk.getMappingKey());
      return new TextRange(start, start + chunk.getMappingKey().length());
    }
    return shifted;
  }

  @Nullable
  @Override
  public PsiElement resolve() {
    PsiElement result = resolveFormatString();
    if (result == null) {
      result = resolvePercentString();
    }
    return result;
  }

  @Nullable
  private PsiElement resolveFormatString() {
    final PyArgumentList argumentList = getArgumentList(getElement());
    if (argumentList != null) {
      final PyExpression[] arguments = argumentList.getArguments();
      if (myChunk.getMappingKey() != null) {
        return argumentList.getKeywordArgument(myChunk.getMappingKey());
      }
      else {
        final int position = myChunk.getPosition() == null ? myPosition : myChunk.getPosition();
        if (arguments.length == 1 && arguments[0] instanceof PyStarArgument) {
          return arguments[0];
        }
        else if (position < arguments.length) {
            return arguments[position];
          }
        }
      }

    return null;
  }

  @Nullable
  private PsiElement resolvePercentString() {
    PsiElement result = null;

    final PyBinaryExpression binaryExpression = PsiTreeUtil.getParentOfType(getElement(), PyBinaryExpression.class);
    if (binaryExpression != null) {
      final PyExpression rightExpression = binaryExpression.getRightExpression();
      if (rightExpression instanceof PyParenthesizedExpression) {
        result = resolvePositional((PyParenthesizedExpression)rightExpression);
      }
      else if (rightExpression instanceof PyDictLiteralExpression) {
        result = resolveKeyword((PyDictLiteralExpression)rightExpression);
      }
    }
    return result == null ? getElement() : result;
  }

  @Nullable
  private PsiElement resolveKeyword(PyDictLiteralExpression rightExpression) {
    if (myChunk.getMappingKey() != null) {
      final PyKeyValueExpression[] keyValueExpressions = rightExpression.getElements();
      for (PyKeyValueExpression keyValueExpression: keyValueExpressions) {
        final PyStringLiteralExpression key = (PyStringLiteralExpression)keyValueExpression.getKey();
          if (key.getStringValue().equals(myChunk.getMappingKey())) {
            return key;
          }
      }
    }
    return null;
  }

  @Nullable
  private PsiElement resolvePositional(PyParenthesizedExpression rightExpression) {
    PsiElement result = null;

    final PyExpression containedExpression = getContainedExpression(rightExpression);
    if (containedExpression instanceof PyTupleExpression) {
      final PyExpression[] elements = ((PySequenceExpression)containedExpression).getElements();
      if (elements.length > myPosition) {
        result = elements[myPosition];
      }
    }
    else if (containedExpression instanceof PyBinaryExpression && ((PyBinaryExpression)containedExpression).isOperator("+")) {
      result = processNotNestedBinaryExpression((PyBinaryExpression)containedExpression);
    }
    return result;
  }

  @Nullable
  private PsiElement processNotNestedBinaryExpression(PyBinaryExpression containedExpression) {
    PyExpression left = containedExpression.getLeftExpression();
    PyExpression right = containedExpression.getRightExpression();
    if (left instanceof PyParenthesizedExpression) {
      PyExpression leftTuple = getContainedExpression((PyParenthesizedExpression)left);
      if (leftTuple instanceof PyTupleExpression) {
        PyExpression[] leftTupleElements = ((PyTupleExpression)leftTuple).getElements();
        int leftTupleLength = leftTupleElements.length;
        if (leftTupleLength > myPosition) {
          return leftTupleElements[myPosition];
        }
        if (right instanceof PyParenthesizedExpression) {
          PyExpression rightTuple = ((PyParenthesizedExpression)right).getContainedExpression();
          if (rightTuple instanceof PyTupleExpression) {
            PyExpression[] rigthTupleElements = ((PyTupleExpression)rightTuple).getElements();
            int rightLength = rigthTupleElements.length;
            if (leftTupleLength + rightLength > myPosition)
              return rigthTupleElements[myPosition - leftTupleLength];
          }
        }
      }
    }
    return null;
  }

  @Nullable
  private static PyExpression getContainedExpression(@NotNull final PyParenthesizedExpression parenthesizedExpression) {
    PyExpression containedExpression = parenthesizedExpression.getContainedExpression();
    while (containedExpression instanceof PyParenthesizedExpression) {
      containedExpression = ((PyParenthesizedExpression)containedExpression).getContainedExpression();
    }
    return containedExpression;
  }

  @Nullable
  private static PyArgumentList getArgumentList(final PsiElement original) {
    final PsiElement pyReferenceExpression = PsiTreeUtil.getParentOfType(original, PyReferenceExpression.class);
    return PsiTreeUtil.getNextSiblingOfType(pyReferenceExpression, PyArgumentList.class);
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }
}
