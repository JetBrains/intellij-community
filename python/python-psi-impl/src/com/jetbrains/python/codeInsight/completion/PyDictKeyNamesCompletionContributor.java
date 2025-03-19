// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypedDictType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * User: catherine
 * <p/>
 * Complete known keys for dictionaries
 */
public final class PyDictKeyNamesCompletionContributor extends CompletionContributor implements DumbAware {
  private static final String DEFAULT_QUOTE = "\"";

  public PyDictKeyNamesCompletionContributor() {
    extend(
      CompletionType.BASIC,
      psiElement().inside(PySubscriptionExpression.class),
      new CompletionProvider<>() {
        @Override
        protected void addCompletions(@NotNull CompletionParameters parameters,
                                      @NotNull ProcessingContext context,
                                      @NotNull CompletionResultSet result) {
          final PsiElement original = parameters.getOriginalPosition();
          if (original == null) return;

          final PySubscriptionExpression subscription = PsiTreeUtil.getParentOfType(original, PySubscriptionExpression.class);
          if (subscription == null) return;

          final PyExpression operand = subscription.getOperand();
          final DictKeyCompletionResultSet dictCompletion =
            new DictKeyCompletionResultSet(result, subscription.getIndexExpression() instanceof PyStringLiteralExpression);

          if (addCompletionIfOperandIsTypedDict(operand, dictCompletion)) {
            return;
          }
          if (operand instanceof PyReferenceExpression) {
            final PyExpression resolvedElement = PyResolveUtil.fullResolveLocally((PyReferenceExpression)operand);
            if (resolvedElement instanceof PyDictLiteralExpression) {
              addDictLiteralKeys((PyDictLiteralExpression)resolvedElement, dictCompletion);
              addAdditionalKeys(parameters.getOriginalFile(), operand, dictCompletion);
            }
            if (resolvedElement instanceof PyCallExpression) {
              addDictConstructorKeys((PyCallExpression)resolvedElement, dictCompletion);
              addAdditionalKeys(parameters.getOriginalFile(), operand, dictCompletion);
            }
          }
        }
      }
    );
  }

  /**
   * Add index expression completion if an operand is a TypedDict
   *
   * @return true if an operand is a TypedDict
   */
  private static boolean addCompletionIfOperandIsTypedDict(final @NotNull PyExpression operand,
                                                           final @NotNull DictKeyCompletionResultSet dictCompletion) {
    final TypeEvalContext typeEvalContext = TypeEvalContext.codeCompletion(operand.getProject(), operand.getContainingFile());
    final PyType type = typeEvalContext.getType(operand);
    if (type instanceof PyTypedDictType dictType) {
      for (String key : dictType.getFields().keySet()) {
        dictCompletion.addKey(key);
      }
      return true;
    }
    return false;
  }

  /**
   * add keys to completion result from dict constructor
   */
  private static void addDictConstructorKeys(final PyCallExpression dictConstructor, final DictKeyCompletionResultSet result) {
    final PyExpression callee = dictConstructor.getCallee();
    if (callee == null) return;
    final String name = callee.getText();
    if ("dict".equals(name)) {
      final TypeEvalContext context = TypeEvalContext.codeCompletion(callee.getProject(), callee.getContainingFile());
      final PyType type = context.getType(dictConstructor);
      if (type != null && type.isBuiltin()) {
        final PyArgumentList list = dictConstructor.getArgumentList();
        if (list == null) return;
        final PyExpression[] argumentList = list.getArguments();
        for (final PyExpression argument : argumentList) {
          if (argument instanceof PyKeywordArgument keywordArgument) {
            String keyword = keywordArgument.getKeyword();
            if (keyword != null) {
              result.addKey(keyword);
            }
          }
        }
      }
    }
  }

  /**
   * add keys from assignment statements
   * For instance, dictionary['b']=b
   *
   * @param file    to get additional keys
   * @param operand is operand of origin element
   * @param result  is completion result set
   */
  private static void addAdditionalKeys(final PsiFile file, final PsiElement operand, final DictKeyCompletionResultSet result) {
    Collection<PySubscriptionExpression> subscriptionExpressions = PsiTreeUtil.findChildrenOfType(file, PySubscriptionExpression.class);
    for (PySubscriptionExpression expr : subscriptionExpressions) {
      if (expr.getOperand().getText().equals(operand.getText()) &&
          expr.getParent() instanceof PyAssignmentStatement assignmentStatement &&
          expr.equals(assignmentStatement.getLeftHandSideExpression()) &&
          expr.getIndexExpression() instanceof PyStringLiteralExpression key) {
        result.addKey(key);
      }
    }
  }

  /**
   * add keys from dict literal expression
   */
  private static void addDictLiteralKeys(final PyDictLiteralExpression dict, final DictKeyCompletionResultSet result) {
    PyKeyValueExpression[] keyValues = dict.getElements();
    for (PyKeyValueExpression expression : keyValues) {
      if (expression.getKey() instanceof PyStringLiteralExpression key) {
        result.addKey(key);
      }
    }
  }

  private static class DictKeyCompletionResultSet {
    private final @NotNull CompletionResultSet myResult;
    private final boolean myIsInsideString;

    DictKeyCompletionResultSet(@NotNull CompletionResultSet result, boolean isInsideString) {
      myResult = result;
      myIsInsideString = isInsideString;
    }

    void addKey(@NotNull PyStringLiteralExpression keyExpression) {
      if (myIsInsideString) {
        addElement(keyExpression.getStringValue());
      }
      else {
        addElement(keyExpression.getText());
      }
    }

    void addKey(@NotNull String key) {
      String lookupString = myIsInsideString ? key : "%s%s%s".formatted(DEFAULT_QUOTE, key, DEFAULT_QUOTE);
      addElement(lookupString);
    }

    private void addElement(@NotNull String lookupString) {
      myResult.addElement(LookupElementBuilder
                            .create(lookupString)
                            .withTypeText("dict key")
                            .withIcon(IconManager.getInstance().getPlatformIcon(PlatformIcons.Parameter)));
    }
  }
}
