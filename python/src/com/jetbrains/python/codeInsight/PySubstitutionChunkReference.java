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
import com.intellij.util.ObjectUtils;
import com.jetbrains.python.inspections.PyStringFormatParser;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PySubstitutionChunkReference extends PsiReferenceBase<PyStringLiteralExpression> implements PsiReferenceEx{
  private final int myPosition;
  private final PyStringFormatParser.SubstitutionChunk myChunk;

  public PySubstitutionChunkReference(PyStringLiteralExpression element, @NotNull final PyStringFormatParser.SubstitutionChunk chunk, final int position) {
    super(element, getKeyWordRange(element, chunk));
    myChunk = chunk;
    myPosition = position;
  }
  @Nullable
  @Override
  public HighlightSeverity getUnresolvedHighlightSeverity(TypeEvalContext context) {
    return HighlightSeverity.WEAK_WARNING;
  }

  @Nullable
  @Override
  public String getUnresolvedDescription() {
    return null;
  }

  private static TextRange getKeyWordRange(PyStringLiteralExpression element, PyStringFormatParser.SubstitutionChunk chunk) {
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
    PsiElement result = null;
    if (myChunk != null) {
      result = resolveFormatString();
      if (result == null) {
        result = resolvePercentString();
      }
    }
    return result;
  }

  @Nullable
  private PsiElement resolveFormatString() {
    PyArgumentList argumentList = getArgumentList(getElement());
    if (argumentList != null) {
      PyExpression[] arguments = argumentList.getArguments();
      if (myChunk.getMappingKey() != null) {
        PyKeywordArgument argument = argumentList.getKeywordArgument(myChunk.getMappingKey());
        PyExpression subStarExpression = getSubStarExpression(arguments);
        return ObjectUtils.chooseNotNull(argument, subStarExpression);
      }
      else {
        int position = myChunk.getPosition() == null ? myPosition : myChunk.getPosition();
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

  private PsiElement resolvePercentString() {
    PyBinaryExpression binaryExpression = PsiTreeUtil.getParentOfType(getElement(), PyBinaryExpression.class);
    final Class[] SIMPLE_RESULT_EXPRESSIONS = {
      PyLiteralExpression.class, PySubscriptionExpression.class, PyBinaryExpression.class, PyConditionalExpression.class,
      PyCallExpression.class, PySliceExpression.class, PyReferenceExpression.class
    };

    if (binaryExpression != null) {
      PyExpression rightExpression = binaryExpression.getRightExpression();

      if (rightExpression instanceof PyParenthesizedExpression) {
        PyParenthesizedExpression expression = (PyParenthesizedExpression)rightExpression;
        PyExpression containedExpression = expression.getContainedExpression();
        if (containedExpression instanceof PyTupleExpression ) {
          PyExpression[] elements = ((PySequenceExpression)containedExpression).getElements();
          if (elements.length > myPosition) {
            return elements[myPosition];
          }
        }
      }
      else if (rightExpression instanceof PyDictLiteralExpression) {
        if (myChunk.getMappingKey() != null) {
          PyKeyValueExpression[] keyValueExpressions = ((PyDictLiteralExpression)rightExpression).getElements();
          for (PyKeyValueExpression keyValueExpression: keyValueExpressions) {
            PyStringLiteralExpression key = (PyStringLiteralExpression)keyValueExpression.getKey();
              if (key.getStringValue().equals(myChunk.getMappingKey())) {
                return key;
              }
          }
        }
      }
      else if (PyUtil.instanceOf(rightExpression, SIMPLE_RESULT_EXPRESSIONS)) {
        return rightExpression;
      }
    }
    return null;
  }

  @Nullable
  private static PyArgumentList getArgumentList(PsiElement original) {
    PsiElement pyReferenceExpression = PsiTreeUtil.getParentOfType(original, PyReferenceExpression.class);
    return PsiTreeUtil.getNextSiblingOfType(pyReferenceExpression, PyArgumentList.class);
  }

  @Nullable
  private static PyExpression getSubStarExpression(@NotNull final PyExpression[] args) {
    if (args.length == 1 && args[0] instanceof PyStarArgument) {
      return PsiTreeUtil.getChildOfAnyType(args[0], PyDictLiteralExpression.class, PyReferenceExpression.class);
    }
    return null;
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }
}
