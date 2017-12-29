// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.jetbrains.python.inspections.PyStringFormatParser.NewStyleSubstitutionChunk;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class PySubstitutionChunkReference extends PsiReferenceBase<PyStringLiteralExpression> implements PsiReferenceEx {
  private final int myPosition;
  @NotNull private final PyStringFormatParser.SubstitutionChunk myChunk;
  private final boolean myIsPercent;

  public PySubstitutionChunkReference(@NotNull final PyStringLiteralExpression element,
                                      @NotNull final PyStringFormatParser.SubstitutionChunk chunk, final int position) {
    super(element, getKeywordRange(element, chunk));
    myChunk = chunk;
    myPosition = position;
    myIsPercent = chunk instanceof PyStringFormatParser.PercentSubstitutionChunk;
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
      return TextRange.from(start, chunk.getMappingKey().length());
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
    return myChunk.getMappingKey() != null ? resolveKeywordFormat(argumentList).get() : resolvePositionalFormat(argumentList);
  }

  @Nullable
  private PsiElement resolvePositionalFormat(@NotNull PyArgumentList argumentList) {
    final int position = myChunk.getPosition() == null ? myPosition : myChunk.getPosition();
    int n = 0;
    boolean notSureAboutStarArgs = false;
    PyStarArgument firstStarArg = null;
    for (PyExpression arg : argumentList.getArguments()) {
      final PyStarArgument starArg = PyUtil.as(arg, PyStarArgument.class);
      if (starArg != null) {
        if (!starArg.isKeyword()) {
          if (firstStarArg == null) {
            firstStarArg = starArg;
          }
          // TODO: Support multiple *args for Python 3.5+
          final Ref<PyExpression> resolvedRef = resolvePositionalStarExpression(starArg, n);
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
      }
      else if (!(arg instanceof PyKeywordArgument)) {
        if (position == n) {
          return arg;
        }
        n++;
      }
    }
    return notSureAboutStarArgs ? firstStarArg : null;
  }

  @NotNull
  private Ref<PyExpression> resolveKeywordFormat(@NotNull PyArgumentList argumentList) {
    final Ref<PyExpression> valueExprRef = getKeyValueFromArguments(argumentList);
    final String indexElement = myChunk instanceof NewStyleSubstitutionChunk ? ((NewStyleSubstitutionChunk)myChunk).getMappingKeyElementIndex() : null;
    if (valueExprRef != null && !valueExprRef.isNull() && indexElement != null) {
      final PyExpression valueExpr = PyPsiUtils.flattenParens(valueExprRef.get());
      assert valueExpr != null;
      try {
        final Integer index = Integer.valueOf(indexElement);
        final Ref<PyExpression> resolvedRef = resolveNumericIndex(valueExpr, index);
        if (resolvedRef != null) return resolvedRef;
      }
      catch (NumberFormatException e) {
        final Ref<PyExpression> resolvedRef = resolveStringIndex(valueExpr, indexElement);
        if (resolvedRef != null) return resolvedRef;
      }
    }
    // valueExprRef is null only if there's no corresponding keyword argument and no star arguments
    return valueExprRef == null ? Ref.create() : valueExprRef;
  }

  @Nullable
  private Ref<PyExpression> getKeyValueFromArguments(@NotNull PyArgumentList argumentList) {
    final PyKeywordArgument valueFromKeywordArg = argumentList.getKeywordArgument(myChunk.getMappingKey());
    final List<PyStarArgument> keywordStarArgs = getStarArguments(argumentList, true);

    Ref<PyExpression> valueExprRef = null;
    if (valueFromKeywordArg != null) {
      valueExprRef = Ref.create(valueFromKeywordArg.getValueExpression());
    }
    else if (!keywordStarArgs.isEmpty()){
      for (PyStarArgument arg : keywordStarArgs) {
        final Ref<PyExpression> resolvedRef = resolveKeywordStarExpression(arg);
        if (resolvedRef != null && (valueExprRef == null || valueExprRef.get() == null)) {
          valueExprRef = resolvedRef;
        }
      }
      if (valueExprRef == null) {
        valueExprRef = Ref.create(Iterables.getFirst(keywordStarArgs, null));
      }
    }
    return valueExprRef;
  }

  @Nullable
  private Ref<PyExpression> resolveStringIndex(@NotNull PyExpression valueExpr, @NotNull String indexElement) {
    if (valueExpr instanceof PyCallExpression) {
      return resolveDictCall((PyCallExpression)valueExpr, indexElement, false);
    }
    else if (valueExpr instanceof PyDictLiteralExpression) {
      Ref<PyExpression> resolvedRef = getElementFromDictLiteral((PyDictLiteralExpression)valueExpr, indexElement);
      if (resolvedRef != null) return resolvedRef;
    }
    else if (valueExpr instanceof PyReferenceExpression) {
      return Ref.create(valueExpr);
    }

    return null;
  }

  @Nullable
  private Ref<PyExpression> resolveNumericIndex(@NotNull PyExpression valueExpr, @NotNull Integer index) {
    if (PsiTreeUtil.instanceOf(valueExpr, PyListLiteralExpression.class, PyTupleExpression.class, PyStringLiteralExpression.class)) {
      Ref<PyExpression> elementRef = getElementByIndex(valueExpr, index);
      if (elementRef != null) return elementRef;
    }
    else if (valueExpr instanceof PyDictLiteralExpression) {
      return getElementFromDictLiteral((PyDictLiteralExpression)valueExpr, index);
    }
    else if (valueExpr instanceof PyReferenceExpression) {
      return Ref.create(valueExpr);
    }
    return null;
  }

  @Nullable
  private Ref<PyExpression> getElementFromDictLiteral(@NotNull PyDictLiteralExpression valueExpr, @NotNull Integer index) {
    boolean allKeysForSure = true;
    final PyKeyValueExpression[] elements = valueExpr.getElements();
    for (PyKeyValueExpression element : elements) {
      final PyNumericLiteralExpression key = PyUtil.as(element.getKey(), PyNumericLiteralExpression.class);
      if (key != null && new Long(index).equals(key.getLongValue())) {
        return Ref.create(element.getValue());
      }
      else if (!(element.getKey() instanceof PyLiteralExpression)) {
        allKeysForSure = false;
      }
    }

    return resolveDoubleStar(valueExpr, String.valueOf(index), true, allKeysForSure);
  }

  @Nullable
  public static Ref<PyExpression> getElementByIndex(@NotNull PyExpression listTupleExpr, int index) {
    boolean noElementsForSure = true;
    int seenElementsNumber = 0;
    PyExpression[] elements = getElementsFromListOrTuple(listTupleExpr);
    for (PyExpression element : elements) {
      if (element instanceof PyStarExpression) {
        if (!LanguageLevel.forElement(element).isAtLeast(LanguageLevel.PYTHON35)) continue;
        final PyExpression underStarExpr = PyPsiUtils.flattenParens(((PyStarExpression)element).getExpression());
        if (PsiTreeUtil.instanceOf(underStarExpr, PyListLiteralExpression.class, PyTupleExpression.class)) {
          PyExpression[] subsequenсeElements = getElementsFromListOrTuple(underStarExpr);
          int subsequenceElementIndex = index - seenElementsNumber;
          if (subsequenceElementIndex < subsequenсeElements.length) {
            return Ref.create(subsequenсeElements[subsequenceElementIndex]);
          }
          if (noElementsForSure) noElementsForSure = Arrays.stream(subsequenсeElements).noneMatch(it -> it instanceof PyStarExpression);
          seenElementsNumber += subsequenсeElements.length;
        }
        else {
          noElementsForSure = false;
          break;
        }
      }
      else {
        if (index == seenElementsNumber) {
          return Ref.create(element);
        }
        seenElementsNumber++;
      }
    }
    return noElementsForSure ? Ref.create() : null;
  }

  public static PyExpression[] getElementsFromListOrTuple(@NotNull final PyExpression expression) {
    if (expression instanceof PyListLiteralExpression) {
      return ((PyListLiteralExpression)expression).getElements();
    }
    else if (expression instanceof PyTupleExpression) {
      return ((PyTupleExpression)expression).getElements();
    }
    else if (expression instanceof PyStringLiteralExpression) {
      String value = ((PyStringLiteralExpression)expression).getStringValue();
      if (value != null) {
        // Strings might be packed as well as dicts, so we need to resolve somehow to string element.
        // But string element isn't PyExpression so I decided to resolve to PyStringLiteralExpression for
        // every string element
        PyExpression[] result = new PyExpression[value.length()];
        Arrays.fill(result, expression);
        return result;
      }
    }

    return PyExpression.EMPTY_ARRAY;
  }

  @NotNull
  private static List<PyStarArgument> getStarArguments(@NotNull PyArgumentList argumentList,
                                                       @SuppressWarnings("SameParameterValue") boolean isKeyword) {
    return Arrays.stream(argumentList.getArguments())
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
      return isKeyWordSubstitution ? resolveKeywordPercent(rightExpression, myChunk.getMappingKey()) : resolvePositionalPercent(rightExpression);
    }
    return null;
  }

  @Nullable
  private PyExpression resolveKeywordPercent(@NotNull PyExpression expression, @NotNull String key) {
    final PyExpression containedExpr = PyPsiUtils.flattenParens(expression);
    if (PsiTreeUtil.instanceOf(containedExpr, PyDictLiteralExpression.class)) {
      final Ref<PyExpression> resolvedRef = getElementFromDictLiteral((PyDictLiteralExpression)containedExpr, key);
      return resolvedRef != null ? resolvedRef.get() : containedExpr;
    }
    else if (PsiTreeUtil.instanceOf(containedExpr, PyLiteralExpression.class, PySetLiteralExpression.class, PyListLiteralExpression.class,
                                    PyTupleExpression.class)) {
      return null;
    }
    else if (containedExpr instanceof PyCallExpression) {
      if (myChunk.getMappingKey() != null) {
        Ref<PyExpression> elementRef = resolveDictCall((PyCallExpression)containedExpr, myChunk.getMappingKey(), true);
        if (elementRef != null) return elementRef.get();
      }
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
  private PsiElement resolveNotNestedBinaryExpression(@NotNull PyBinaryExpression containedExpression) {
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
        if (right instanceof PyTupleExpression) {
          PyExpression[] rightTupleElements = ((PyTupleExpression)right).getElements();
          int rightLength = rightTupleElements.length;
          if (leftTupleLength + rightLength > myPosition) return rightTupleElements[myPosition - leftTupleLength];
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
  private Ref<PyExpression> resolveKeywordStarExpression(@NotNull PyStarArgument starArgument) {
    // TODO: support call, reference expressions here
    final PyDictLiteralExpression dictExpr = PsiTreeUtil.getChildOfType(starArgument, PyDictLiteralExpression.class);
    final PyCallExpression callExpression = PsiTreeUtil.getChildOfType(starArgument, PyCallExpression.class);
    final String key = myChunk.getMappingKey();
    assert key != null;
    if (dictExpr != null) {
      return getElementFromDictLiteral(dictExpr, key);
    }
    else if (callExpression != null) {
      return resolveDictCall(callExpression, key, false);
    }
    return null;
  }

  @Nullable
  private Ref<PyExpression> resolvePositionalStarExpression(@NotNull PyStarArgument starArgument, int argumentPosition) {
    final PyExpression expr = PyPsiUtils.flattenParens(PsiTreeUtil.getChildOfAnyType(starArgument, PyListLiteralExpression.class, PyParenthesizedExpression.class,
                                                            PyStringLiteralExpression.class));
    if (expr == null) {
      return Ref.create(starArgument);
    }
    final int position = (myChunk.getPosition() != null ? myChunk.getPosition() : myPosition) - argumentPosition;
    return getElementByIndex(expr, position);
  }

  @Nullable
  private Ref<PyExpression> getElementFromDictLiteral(@NotNull PyDictLiteralExpression expression, @NotNull String mappingKey) {
    boolean allKeysForSure = true;
    final PyKeyValueExpression[] keyValueExpressions = expression.getElements();
    for (PyKeyValueExpression keyValueExpression : keyValueExpressions) {
      PyExpression keyExpression = keyValueExpression.getKey();
      if (keyExpression instanceof PyStringLiteralExpression) {
        final PyStringLiteralExpression key = (PyStringLiteralExpression)keyExpression;
        if (key.getStringValue().equals(mappingKey)) {
          return Ref.create(keyValueExpression.getValue());
        }
      }
      else if (!(keyExpression instanceof PyLiteralExpression)) {
        allKeysForSure = false;
      }
    }

    return resolveDoubleStar(expression, mappingKey, false, allKeysForSure);
  }

  @Nullable
  private Ref<PyExpression> resolveDoubleStar(@NotNull PyDictLiteralExpression expression, @NotNull String mappingKey, boolean isNumeric, boolean allKeysForSure) {
    final LanguageLevel languageLevel = LanguageLevel.forElement(expression);
    PyDoubleStarExpression[] starExpressions = PsiTreeUtil.getChildrenOfType(expression, PyDoubleStarExpression.class);
    if (languageLevel.isAtLeast(LanguageLevel.PYTHON35) && starExpressions != null) {
      for (PyDoubleStarExpression expr : starExpressions) {
        PyExpression underStarExpr = PyPsiUtils.flattenParens(expr.getExpression());
        if (underStarExpr != null) {
          if (underStarExpr instanceof PyDictLiteralExpression) {
            Ref<PyExpression> element;
            if (isNumeric) {
              element = getElementFromDictLiteral((PyDictLiteralExpression)underStarExpr, Integer.valueOf(mappingKey));
            }
            else {
              element = getElementFromDictLiteral((PyDictLiteralExpression)underStarExpr, mappingKey);
            }

            allKeysForSure = element != null;
             if (element != null && !element.isNull()) return element;
          }
          else if (underStarExpr instanceof PyCallExpression) {
            Ref<PyExpression> element = resolveDictCall((PyCallExpression)underStarExpr, mappingKey, true);
            allKeysForSure = element != null;
            if (element != null && !element.isNull()) return element;
          }
          else {
            allKeysForSure = false;
          }
        }
      }
    }

    return allKeysForSure ? Ref.create() : null;
  }

  @Nullable
  private Ref<PyExpression> resolveDictCall(@NotNull PyCallExpression expression, @NotNull String key, boolean goDeep) {
    final PyExpression callee = expression.getCallee();
    boolean allKeysForSure = true;
    final LanguageLevel languageLevel = LanguageLevel.forElement(expression);
    if (callee != null) {
      final String name = callee.getName();
      if ("dict".equals(name)) {
        final PyArgumentList argumentList = expression.getArgumentList();
        for (PyExpression arg : expression.getArguments()) {
          if (languageLevel.isAtLeast(LanguageLevel.PYTHON35) && goDeep && arg instanceof PyStarExpression) {
            PyExpression expr = ((PyStarExpression)arg).getExpression();
            if (expr instanceof PyDictLiteralExpression) {
              Ref<PyExpression> element = getElementFromDictLiteral((PyDictLiteralExpression)expr, key);
              if (element != null) return element;
            }
            else if (expr instanceof PyCallExpression) {
              Ref<PyExpression> element = resolveDictCall((PyCallExpression)expr, key, false);
              if (element != null) return element;
            }
            else {
              allKeysForSure = false;
            }
          }
          if (!(arg instanceof PyKeywordArgument)) {
            allKeysForSure = false;
          }
        }
        if (argumentList != null) {
          PyKeywordArgument argument = argumentList.getKeywordArgument(key);
          if (argument != null) {
            return Ref.create(argument);
          }
          else {
            return allKeysForSure ? Ref.create() : null;
          }
        }
      }
    }
    return Ref.create(expression);
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }
}
