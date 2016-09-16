/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.Stack;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonStringUtil;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiPredicate;

/**
 * User: catherine
 *
 * Intention to convert variadic parameter(s) to normal
 * For instance,
 *
 * from:
 * def foo(**kwargs):
 *   doSomething(kwargs['foo'])
 *
 * to:
 * def foo(foo, **kwargs):
 *   doSomething(foo)
 *
 */
public class ConvertVariadicParamIntention extends BaseIntentionAction {

  @Override
  @NotNull
  public String getText() {
    return PyBundle.message("INTN.convert.variadic.param");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.convert.variadic.param");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PyFile)) {
      return false;
    }

    final PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    final PyFunction function = PsiTreeUtil.getParentOfType(element, PyFunction.class);

    if (function != null) {
      for (PyCallExpression call : findKeywordContainerCalls(function)) {
        final PyExpression firstArgument = ArrayUtil.getFirstElement(call.getArguments());
        final String firstArgumentValue = PythonStringUtil.getStringValue(firstArgument);
        if (firstArgumentValue == null || !PyNames.isIdentifierString(firstArgumentValue)) {
          return false;
        }
      }

      for (PySubscriptionExpression subscription : findKeywordContainerSubscriptions(function)) {
        final PyExpression indexExpression = subscription.getIndexExpression();
        final String indexValue = PythonStringUtil.getStringValue(indexExpression);
        if (indexValue == null || !PyNames.isIdentifierString(indexValue)) {
          return false;
        }
      }

      return getKeywordContainer(function) != null;
    }

    return false;
  }

  @Nullable
  private static PyParameter getKeywordContainer(@NotNull PyFunction function) {
    return Arrays
      .stream(function.getParameterList().getParameters())
      .filter(PyNamedParameter.class::isInstance)
      .map(PyNamedParameter.class::cast)
      .filter(PyNamedParameter::isKeywordContainer)
      .findFirst()
      .orElse(null);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    final PyFunction function = PsiTreeUtil.getParentOfType(element, PyFunction.class);

    if (function != null) {
      replaceKeywordContainerSubscriptions(function, project);
      replaceKeywordContainerCalls(function, project);
    }
  }

  @NotNull
  private static List<PySubscriptionExpression> findKeywordContainerSubscriptions(@NotNull PyFunction function) {
    return findKeywordContainerUsages(function, ConvertVariadicParamIntention::isKeywordContainerSubscription);
  }

  @NotNull
  private static List<PyCallExpression> findKeywordContainerCalls(@NotNull PyFunction function) {
    return findKeywordContainerUsages(function, ConvertVariadicParamIntention::isKeywordContainerCall);
  }

  private static void replaceKeywordContainerSubscriptions(@NotNull PyFunction function, @NotNull Project project) {
    final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);

    for (PySubscriptionExpression subscription : findKeywordContainerSubscriptions(function)) {
      Optional
        .ofNullable(subscription.getIndexExpression())
        .map(indexExpression -> PyUtil.as(indexExpression, PyStringLiteralExpression.class))
        .map(PyStringLiteralExpression::getStringValue)
        .map(indexValue -> elementGenerator.createExpressionFromText(LanguageLevel.forElement(function), indexValue))
        .ifPresent(
          parameter -> {
            final int anchorIndex = function.getContainingClass() == null ? 0 : 1;
            final PsiElement comma = (PsiElement)elementGenerator.createComma();

            function.getParameterList().addBefore(parameter, function.getParameterList().getParameters()[anchorIndex]);
            function.getParameterList().addBefore(comma, function.getParameterList().getParameters()[anchorIndex]);

            subscription.replace(parameter);
          }
        );
    }
  }

  private static void replaceKeywordContainerCalls(@NotNull PyFunction function, @NotNull Project project) {
    final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);

    for (PyCallExpression call : findKeywordContainerCalls(function)) {
      Optional
        .of(call.getArguments())
        .map(ArrayUtil::getFirstElement)
        .map(firstArgument -> PyUtil.as(firstArgument, PyStringLiteralExpression.class))
        .map(PyStringLiteralExpression::getStringValue)
        .ifPresent(
          indexValue -> {
            final PyNamedParameter parameterWithDefaultValue = getParameterWithDefaultValue(elementGenerator, call, indexValue);
            final PyExpression parameter = elementGenerator.createExpressionFromText(LanguageLevel.forElement(function), indexValue);
            final PyParameter keywordContainer = getKeywordContainer(function);

            if (parameter != null) {
              if (parameterWithDefaultValue != null) {
                function.getParameterList().addBefore(parameterWithDefaultValue, keywordContainer);
              }
              else {
                function.getParameterList().addBefore(parameter, keywordContainer);
              }
              function.getParameterList().addBefore((PsiElement)elementGenerator.createComma(), keywordContainer);

              call.replace(parameter);
            }
          }
        );
    }
  }

  @NotNull
  private static <T> List<T> findKeywordContainerUsages(@NotNull PyFunction function,
                                                        @NotNull BiPredicate<PsiElement, String> usagePredicate) {
    final PyParameter keywordContainer = getKeywordContainer(function);
    final String keywordContainerName = keywordContainer == null ? null : keywordContainer.getName();

    if (keywordContainerName != null) {
      final List<T> result = new ArrayList<T>();
      final Stack<PsiElement> stack = new Stack<PsiElement>();

      for (PyStatement statement : function.getStatementList().getStatements()) {
        stack.push(statement);

        while (!stack.isEmpty()) {
          final PsiElement element = stack.pop();

          if (usagePredicate.test(element, keywordContainerName)) {
            //noinspection unchecked
            result.add((T)element);
          }
          else {
            for (PsiElement child : element.getChildren()) {
              stack.push(child);
            }
          }
        }
      }

      return result;
    }

    return Collections.emptyList();
  }

  private static boolean isKeywordContainerSubscription(@Nullable PsiElement element, @NotNull String keywordContainerName) {
    return Optional
      .ofNullable(PyUtil.as(element, PySubscriptionExpression.class))
      .map(PySubscriptionExpression::getOperand)
      .map(PyExpression::getText)
      .filter(text -> text.equals(keywordContainerName))
      .isPresent();
  }

  private static boolean isKeywordContainerCall(@Nullable PsiElement element, @NotNull String keywordContainerName) {
    return Optional
      .ofNullable(PyUtil.as(element, PyCallExpression.class))
      .map(PyCallExpression::getCallee)
      .map(callee -> PyUtil.as(callee, PyQualifiedExpression.class))
      .filter(
        callee -> {
          final PyExpression qualifier = callee.getQualifier();
          return qualifier != null &&
                 qualifier.getText().equals(keywordContainerName) &&
                 ArrayUtil.contains(callee.getReferencedName(), "get", PyNames.GETITEM);
        }
      )
      .isPresent();
  }

  @Nullable
  private static PyNamedParameter getParameterWithDefaultValue(@NotNull PyElementGenerator elementGenerator,
                                                               @NotNull PyCallExpression call,
                                                               @NotNull String parameterName) {
    final PyExpression[] arguments = call.getArguments();
    if (arguments.length > 1) {
      return elementGenerator.createParameter(parameterName + "=" + arguments[1].getText());
    }

    final PyQualifiedExpression callee = PyUtil.as(call.getCallee(), PyQualifiedExpression.class);
    if (callee != null && "get".equals(callee.getReferencedName())) {
      return elementGenerator.createParameter(parameterName + "=" + PyNames.NONE);
    }

    return null;
  }
}
