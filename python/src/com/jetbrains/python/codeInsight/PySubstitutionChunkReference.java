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
  private boolean myIgnoreUnresolved = false;

  public PySubstitutionChunkReference(@NotNull final PyStringLiteralExpression element,
                                      @NotNull final PyStringFormatParser.SubstitutionChunk chunk, final int position) {
    super(element, getKeyWordRange(element, chunk));
    myChunk = chunk;
    myPosition = position;
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
    boolean isPercentString = myElement.getParent() instanceof PyBinaryExpression;
     if (isPercentString) {
      return resolvePercentString();
    }
    else {
      return resolveFormatString();
    }
  }

  @Nullable
  private PsiElement resolveFormatString() {
    final PyArgumentList argumentList = getArgumentList(getElement());
    if (argumentList != null && argumentList.getArguments().length > 0) {
      final PyExpression[] arguments = argumentList.getArguments();
      
      boolean isStarArgument = arguments.length == 1 && arguments[0] instanceof PyStarArgument;
      if (isStarArgument) return getUnderStarExpression(arguments);
      
      boolean isKeywordSubstitution = myChunk.getMappingKey() != null;
      if (isKeywordSubstitution) {
        return argumentList.getKeywordArgument(myChunk.getMappingKey());
      }
      else {
        final int position = myChunk.getPosition() == null ? myPosition : myChunk.getPosition();
        if (position < arguments.length) return arguments[position];
      
        if (arguments[0] instanceof PyBinaryExpression && ((PyBinaryExpression)arguments[0]).isOperator("+")) {
          return processNotNestedBinaryExpression((PyBinaryExpression)arguments[0]);
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
      
      boolean isKeyWordSubstitution = myChunk.getMappingKey() != null;
      result = isKeyWordSubstitution? resolveKeyword(rightExpression) : resolvePositional(rightExpression);
    }
    return result;
  }

  @Nullable
  private PsiElement resolveKeyword(PyExpression pyExpression) {
    PyExpression expression = pyExpression;
    if (pyExpression instanceof  PyParenthesizedExpression) {
      expression = getContainedExpression((PyParenthesizedExpression)pyExpression);
    }
    
    myIgnoreUnresolved = expression instanceof PyReferenceExpression;
    if (expression instanceof PyDictLiteralExpression) {
      final PyKeyValueExpression[] keyValueExpressions = ((PyDictLiteralExpression)expression).getElements();
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
  private PsiElement resolvePositional(PyExpression expression) {
    PsiElement result = null;
    if (expression instanceof PyParenthesizedExpression) {
      final PyExpression containedExpression = getContainedExpression((PyParenthesizedExpression)expression);
      
      if (containedExpression instanceof PyTupleExpression) {
        final PyExpression[] elements = ((PySequenceExpression)containedExpression).getElements();
        if (elements.length > myPosition) {
          result = elements[myPosition];
        }
      }
      else if (containedExpression instanceof PyBinaryExpression && ((PyBinaryExpression)containedExpression).isOperator("+")) {
        result = processNotNestedBinaryExpression((PyBinaryExpression)containedExpression);
      }
      else if (containedExpression instanceof PyReferenceExpression) {
        myIgnoreUnresolved = true;
      }
    }
    else if (expression instanceof PyReferenceExpression) {
      myIgnoreUnresolved = true;
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
            PyExpression[] rightTupleElements = ((PyTupleExpression)rightTuple).getElements();
            int rightLength = rightTupleElements.length;
            if (leftTupleLength + rightLength > myPosition)
              return rightTupleElements[myPosition - leftTupleLength];
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

  @Nullable
  private PyExpression getUnderStarExpression(@NotNull final PyExpression[] args) {
    if (args.length == 1 && args[0] instanceof PyStarArgument) {
      PyExpression pyExpression = PsiTreeUtil.getChildOfAnyType(args[0], PyDictLiteralExpression.class,
                                                                      PyParenthesizedExpression.class,
                                                                      PyListLiteralExpression.class );
      if (pyExpression != null) {
        boolean isKeywordSubstitution = myChunk.getMappingKey() != null;
        
        if (isKeywordSubstitution && pyExpression instanceof PyDictLiteralExpression) {
          PyDictLiteralExpression dictLiteralExpression = (PyDictLiteralExpression)pyExpression;
          for (PyKeyValueExpression keyValueExpression : dictLiteralExpression.getElements()) {
            if (keyValueExpression.getKey() instanceof PyStringLiteralExpression) {
              PyStringLiteralExpression key = (PyStringLiteralExpression)keyValueExpression.getKey();
              if (key.getStringValue().equals(myChunk.getMappingKey())) {
                return key;
              }
            }
          }
        }
        else {
          int position = myChunk.getPosition() != null ? myChunk.getPosition() : myPosition;
          PyExpression[] elements = null;
          if (pyExpression instanceof PyListLiteralExpression) {
            elements = ((PyListLiteralExpression)pyExpression).getElements();
          }
          else if (pyExpression instanceof PyParenthesizedExpression) {
            PyExpression expression = getContainedExpression((PyParenthesizedExpression)pyExpression);
            if (expression instanceof PyTupleExpression) {
              elements = ((PyTupleExpression)expression).getElements();
              }
            }

          if (elements != null && position < elements.length) {
            return elements[position];
          }
        }        
      }
      else if (PsiTreeUtil.getChildOfType(args[0], PyReferenceExpression.class) != null) {
        myIgnoreUnresolved = true;
      }
    }
    return null;
  }
  @NotNull
  @Override
  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  public boolean ignoreUnresolved() {
    return myIgnoreUnresolved;
  }
}
