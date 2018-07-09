// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.completion;


import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.inspections.PyStringFormatParser;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.StandardPatterns.or;

public class PyStringFormatCompletionContributor extends CompletionContributor {
  private static final String DICT_NAME = "dict";

  private static final PatternCondition<PyReferenceExpression> FORMAT_CALL_PATTERN_CONDITION =
    new PatternCondition<PyReferenceExpression>("isFormatFunction") {

      @Override
      public boolean accepts(@NotNull PyReferenceExpression expression, ProcessingContext context) {
        String expressionName = expression.getName();
        return expressionName != null && expressionName.equals(PyNames.FORMAT);
      }
    };

  private static final PatternCondition<PyReferenceExpression> DICT_CALL_PATTERN_CONDITION =
    new PatternCondition<PyReferenceExpression>("isDictCall") {

      @Override
      public boolean accepts(@NotNull PyReferenceExpression expression, ProcessingContext context) {
        String expressionName = expression.getName();
        return expressionName != null && expressionName.equals(DICT_NAME);
      }
    };

  private static final PsiElementPattern.Capture<PyStringLiteralExpression> FORMAT_STRING_CAPTURE =
    psiElement(PyStringLiteralExpression.class)
      .withParent(psiElement(PyReferenceExpression.class).with(FORMAT_CALL_PATTERN_CONDITION))
      .withSuperParent(2, PyCallExpression.class);

  private static final PsiElementPattern.Capture<PyStringLiteralExpression> PERCENT_STRING_CAPTURE =
    psiElement(PyStringLiteralExpression.class).beforeLeaf(psiElement().withText("%")).withParent(PyBinaryExpression.class);


  @Nullable private static final PatternCondition<PyBinaryExpression> PERCENT_BINARY_EXPRESSION_PATTERN =
    new PatternCondition<PyBinaryExpression>("isBinaryFormatExpression") {
      @Override
      public boolean accepts(@NotNull PyBinaryExpression expression, ProcessingContext context) {
        return expression.isOperator("%");
      }
    };

  private static final PsiElementPattern.Capture<PyKeywordArgument> DICT_FUNCTION_KEYWORD_ARGUMENT_CAPTURE =
    (psiElement(PyKeywordArgument.class))
      .withSuperParent(3,
                       psiElement(PyBinaryExpression.class)
                         .withChild(psiElement(PyCallExpression.class)
                                      .withChild(psiElement(PyReferenceExpression.class)
                                                   .with(DICT_CALL_PATTERN_CONDITION)))
                         .with(PERCENT_BINARY_EXPRESSION_PATTERN));

  private static final PsiElementPattern.Capture<PyReferenceExpression> DICT_FUNCTION_REFERENCE_ARGUMENT_CAPTURE =
    (psiElement(PyReferenceExpression.class))
      .withSuperParent(3,
                       psiElement(PyBinaryExpression.class)
                         .withChild(psiElement(PyCallExpression.class)
                                      .withChild(psiElement(PyReferenceExpression.class)
                                                   .with(DICT_CALL_PATTERN_CONDITION)))
                         .with(PERCENT_BINARY_EXPRESSION_PATTERN));

  private static final PsiElementPattern.Capture<PyKeywordArgument> FORMAT_FUNCTION_ARGUMENT_CAPTURE =
    psiElement(PyKeywordArgument.class)
      .withSuperParent(2, psiElement(PyCallExpression.class)
        .withChild(psiElement(PyReferenceExpression.class).with(FORMAT_CALL_PATTERN_CONDITION)));

  // to provide completion for: "{foo}".format(fo<caret>)
  private static final PsiElementPattern.Capture<PyReferenceExpression> FORMAT_FUNCTION_REFERENCE_ARGUMENT_CAPTURE =
    psiElement(PyReferenceExpression.class)
      .withSuperParent(2, psiElement(PyCallExpression.class)
        .withChild(psiElement(PyReferenceExpression.class).with(FORMAT_CALL_PATTERN_CONDITION)));

  private static final PsiElementPattern.Capture<PyStringLiteralExpression> DICT_LITERAL_STRING_KEY_CAPTURE =
    psiElement(PyStringLiteralExpression.class)
      .withParent(or(psiElement(PyKeyValueExpression.class)
                       .withParent(psiElement(PyDictLiteralExpression.class)
                                     .withParent(psiElement(PyBinaryExpression.class).with(PERCENT_BINARY_EXPRESSION_PATTERN))),
                     psiElement(PyDictLiteralExpression.class)
                       .withParent(psiElement(PyBinaryExpression.class).with(PERCENT_BINARY_EXPRESSION_PATTERN))));

  // to provide completion for: "%(foo)s % {"f<caret>"}
  private static final PsiElementPattern.Capture<PyStringLiteralExpression> SET_LITERAL_STRING_KEY_CAPTURE =
    psiElement(PyStringLiteralExpression.class)
      .withParent(psiElement(PySetLiteralExpression.class).withParent(psiElement(PyBinaryExpression.class)
                                                                        .with(PERCENT_BINARY_EXPRESSION_PATTERN)));

  public PyStringFormatCompletionContributor() {
    extend(
      CompletionType.BASIC,
      or(
        psiElement().inside(PERCENT_STRING_CAPTURE),
        psiElement().inside(FORMAT_STRING_CAPTURE)),
      new StringFormatCompletionProvider()
    );

    extend(
      CompletionType.BASIC,
      or(psiElement().inside(DICT_LITERAL_STRING_KEY_CAPTURE),
         psiElement().inside(DICT_FUNCTION_KEYWORD_ARGUMENT_CAPTURE),
         psiElement().inside(DICT_FUNCTION_REFERENCE_ARGUMENT_CAPTURE),
         psiElement().inside(SET_LITERAL_STRING_KEY_CAPTURE),
         psiElement().inside(FORMAT_FUNCTION_ARGUMENT_CAPTURE),
         psiElement().inside(FORMAT_FUNCTION_REFERENCE_ARGUMENT_CAPTURE)
      ),
      new StringFormatArgumentsCompletionProvider()
    );
  }

  private static class StringFormatArgumentsCompletionProvider extends CompletionProvider<CompletionParameters> {

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
      final PsiElement original = parameters.getOriginalPosition();
      if (original != null) {
        result = result.withPrefixMatcher(getPrefix(parameters.getOffset(), parameters.getOriginalFile()));
        final PsiElement parent = original.getParent();
        if (parent.getParent() instanceof PyKeyValueExpression || parent instanceof PyStringLiteralExpression) {
          final PyBinaryExpression binExpr = PsiTreeUtil.getParentOfType(parent, PyBinaryExpression.class);
          if (binExpr != null) {
            final PyStringLiteralExpression strExpr = PyUtil.as(binExpr.getLeftExpression(), PyStringLiteralExpression.class);
            if (strExpr != null) {
              result.addAllElements(getPercentLookupBuilders(strExpr));
            }
          }
        }
        else if (PsiTreeUtil.instanceOf(parent, PyKeywordArgument.class, PyReferenceExpression.class)) {
          result.addAllElements(getElementsFromString(PsiTreeUtil.getParentOfType(original, PyArgumentList.class)));
        }
      }
    }

    @NotNull
    private static List<LookupElement> getElementsFromString(@Nullable final PyArgumentList argumentList) {
      if (argumentList != null) {
        final PyReferenceExpression refExpr = PsiTreeUtil.getPrevSiblingOfType(argumentList, PyReferenceExpression.class);
        final PyStringLiteralExpression strExpr = PsiTreeUtil.getChildOfType(refExpr, PyStringLiteralExpression.class);
        if (strExpr != null) {
          return getFormatLookupBuilders(strExpr);
        }
        else {
          final PyBinaryExpression binExpr = PsiTreeUtil.getParentOfType(refExpr, PyBinaryExpression.class);
          if (binExpr != null) {
            final PyStringLiteralExpression stringLiteralExpr = PyUtil.as(binExpr.getLeftExpression(), PyStringLiteralExpression.class);
            if (stringLiteralExpr != null) {
              return getPercentLookupBuilders(stringLiteralExpr);
            }
          }
        }
      }
      return Collections.emptyList();
    }

    @NotNull
    private static List<LookupElement> getFormatLookupBuilders(@NotNull final PyStringLiteralExpression expression) {
      final Map<String, PyStringFormatParser.SubstitutionChunk> chunks = PyStringFormatParser.getKeywordSubstitutions(
        PyStringFormatParser.filterSubstitutions(PyStringFormatParser.parseNewStyleFormat(expression.getText())));
      return getLookupBuilders(chunks);
    }

    private static List<LookupElement> getPercentLookupBuilders(@NotNull final PyStringLiteralExpression expression) {
      final Map<String, PyStringFormatParser.SubstitutionChunk> chunks = PyStringFormatParser.getKeywordSubstitutions(
        PyStringFormatParser.filterSubstitutions(PyStringFormatParser.parsePercentFormat(expression.getText())));
      return getLookupBuilders(chunks);
    }

    @NotNull
    private static List<LookupElement> getLookupBuilders(@NotNull final Map<String, PyStringFormatParser.SubstitutionChunk> chunks) {
      return chunks.keySet().stream()
        .map(PyStringFormatCompletionContributor::createLookUpElement)
        .collect(Collectors.toList());
    }
  }

  private static class StringFormatCompletionProvider extends CompletionProvider<CompletionParameters> {
    @Override
    protected void addCompletions(@NotNull final CompletionParameters parameters,
                                  final ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
      final PsiElement original = parameters.getOriginalPosition();
      if (original != null) {
        final PsiElement parent = original.getParent();
        result = result.withPrefixMatcher(getPrefix(parameters.getOffset(), parent.getContainingFile()));

        if (parent instanceof PyStringLiteralExpression) {
          result.addAllElements(addCompletionsForSubstitutions(parameters, original, (PyStringLiteralExpression)parent));
        }
      }
    }

    @NotNull
    private static List<LookupElement> addCompletionsForSubstitutions(@NotNull final CompletionParameters parameters,
                                                                      @NotNull final PsiElement original,
                                                                      @NotNull final PyStringLiteralExpression stringExpression) {
      final int stringOffset = getCaretStartOffsetInsideString(parameters, stringExpression);

      if (isInsideFormatSubstitutionChunk(stringExpression, stringOffset)) {
        final PyExpression[] arguments = getFormatFunctionKeyWordArguments(original);
        final ArrayList<LookupElement> elements = new ArrayList<>();
        for (PyExpression argument : arguments) {
          if (argument instanceof PyKeywordArgument) {
            elements.add(getKeywordArgument((PyKeywordArgument)argument));
          }
          else if (argument instanceof PyStarArgument) {
            elements.addAll(getKeysFromStarArgument((PyStarArgument)argument));
          }
        }
        return elements;
      }

      if (isInsidePercentSubstitutionChunk(stringExpression, stringOffset)) {
        final PyBinaryExpression binExpr = PyUtil.as(PsiTreeUtil.getParentOfType(stringExpression, PyBinaryExpression.class),
                                                     PyBinaryExpression.class);
        if (binExpr != null) {
          final PyExpression rightExpr = PyPsiUtils.flattenParens(binExpr.getRightExpression());

          final PyDictLiteralExpression dict = PyUtil.as(rightExpr, PyDictLiteralExpression.class);
          if (dict != null) {
            return getElementsFromDict(dict);
          }

          final PyCallExpression callExpression = PyUtil.as(rightExpr, PyCallExpression.class);
          if (callExpression != null) {
            final PyExpression callee = callExpression.getCallee();
            if (callee != null && callee.getName() != null && callee.getName().equals(DICT_NAME)) {
              final PyExpression[] arguments = callExpression.getArguments();
              return Arrays.stream(arguments)
                .filter(a -> a instanceof PyKeywordArgument)
                .map(a -> getKeywordArgument((PyKeywordArgument)a))
                .filter(e -> e != null)
                .collect(Collectors.toList());
            }
          }
        }
      }
      return Collections.emptyList();
    }

    private static int getCaretStartOffsetInsideString(@NotNull final CompletionParameters parameters,
                                                       @NotNull final PyStringLiteralExpression parent) {
      final int caretAbsoluteOffset = parameters.getOffset();
      final int stringExprStartOffset = parameters.getPosition().getTextRange().getStartOffset();
      final int stringValueStartOffset = parent.getStringValueTextRange().getStartOffset();
      return caretAbsoluteOffset - stringExprStartOffset - stringValueStartOffset;
    }

    private static boolean isInsideFormatSubstitutionChunk(@NotNull final PyStringLiteralExpression expression, final int offset) {
      List<PyStringFormatParser.SubstitutionChunk> substitutions = PyStringFormatParser.filterSubstitutions(
        PyStringFormatParser.parseNewStyleFormat(expression.getStringValue()));
      return isInsideSubstitutionChunk(offset, substitutions);
    }

    private static boolean isInsidePercentSubstitutionChunk(@NotNull final PyStringLiteralExpression expression, final int offset) {
      List<PyStringFormatParser.SubstitutionChunk> substitutions = PyStringFormatParser.filterSubstitutions(
        PyStringFormatParser.parsePercentFormat(expression.getStringValue()));
      return isInsideSubstitutionChunk(offset, substitutions);
    }

    private static boolean isInsideSubstitutionChunk(int offset, @NotNull List<PyStringFormatParser.SubstitutionChunk> substitutions) {
      return substitutions.stream().anyMatch(s -> offset >= s.getStartIndex() && offset <= s.getEndIndex());
    }

    @NotNull
    private static PyExpression[] getFormatFunctionKeyWordArguments(@NotNull final PsiElement original) {
      final PsiElement pyReferenceExpression = PsiTreeUtil.getParentOfType(original, PyReferenceExpression.class);
      final PyArgumentList argumentList = PsiTreeUtil.getNextSiblingOfType(pyReferenceExpression, PyArgumentList.class);
      return argumentList != null ? argumentList.getArguments() : PyExpression.EMPTY_ARRAY;
    }

    @NotNull
    private static List<LookupElement> getKeysFromStarArgument(@NotNull final PyStarArgument arg) {
      final PyDictLiteralExpression dict = ObjectUtils.chooseNotNull(PsiTreeUtil.getChildOfType(arg, PyDictLiteralExpression.class),
                                                                     getDictFromReference(arg));

      return dict != null ? getElementsFromDict(dict) : Collections.emptyList();
    }

    @NotNull
    private static List<LookupElement> getElementsFromDict(@NotNull final PyDictLiteralExpression dict) {
      return Arrays.stream(dict.getElements())
        .map(e -> PyUtil.as(e.getKey(), PyStringLiteralExpression.class))
        .filter(k-> k != null)
        .map(k -> createLookUpElement(k.getStringValue()))
        .collect(Collectors.toList());
    }

    @Nullable
    private static PyDictLiteralExpression getDictFromReference(@NotNull final PyExpression arg) {
      final PyReferenceExpression referenceExpression = PsiTreeUtil.getChildOfType(arg, PyReferenceExpression.class);
      if (referenceExpression != null) {
        final PsiElement resolveResult = referenceExpression.getReference().resolve();
        if (resolveResult instanceof PyTargetExpression) {
          final PyAssignmentStatement assignmentStatement = PsiTreeUtil.getParentOfType(resolveResult, PyAssignmentStatement.class);
          return PsiTreeUtil.getChildOfType(assignmentStatement, PyDictLiteralExpression.class);
        }
      }
      return null;
    }

    @Nullable
    private static LookupElement getKeywordArgument(@NotNull final PyKeywordArgument arg) {
      final String keyword = arg.getKeyword();
      return keyword != null ?  createLookUpElement(keyword) : null;
    }
  }

  @NotNull
  private static LookupElement createLookUpElement(@NotNull final String element) {
    return LookupElementBuilder
      .create(element)
      .withTypeText("arg");
  }

  @NotNull
  private static String getPrefix(int offset, @NotNull final PsiFile file) {
    if (offset > 0) {
      offset--;
    }
    final String text = file.getText();
    final StringBuilder prefixBuilder = new StringBuilder();
    while (offset > 0 && Character.isLetterOrDigit(text.charAt(offset))) {
      prefixBuilder.insert(0, text.charAt(offset));
      offset--;
    }
    return prefixBuilder.toString();
  }
}
