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
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import one.util.streamex.StreamEx;
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
public class ConvertVariadicParamIntention extends PyBaseIntentionAction {

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
      if (LanguageLevel.forElement(function).isOlderThan(LanguageLevel.PYTHON30) && function.getParameterList().hasPositionalContainer()) {
        return false;
      }

      final boolean caretInParameterList = PsiTreeUtil.isAncestor(function.getParameterList(), element, true);

      for (PyCallExpression call : findKeywordContainerCalls(function)) {
        final PyExpression firstArgument = ArrayUtil.getFirstElement(call.getArguments());
        final String firstArgumentValue = PyStringLiteralUtil.getStringValue(firstArgument);
        if (firstArgumentValue != null &&
            PyNames.isIdentifier(firstArgumentValue) &&
            (caretInParameterList || PsiTreeUtil.isAncestor(call, element, true))) {
          return true;
        }
      }

      for (PySubscriptionExpression subscription : findKeywordContainerSubscriptions(function)) {
        final PyExpression indexExpression = subscription.getIndexExpression();
        final String indexValue = PyStringLiteralUtil.getStringValue(indexExpression);
        if (indexValue != null &&
            PyNames.isIdentifier(indexValue) &&
            (caretInParameterList || PsiTreeUtil.isAncestor(subscription, element, true))) {
          return true;
        }
      }
    }

    return false;
  }

  @Nullable
  private static PyParameter getKeywordContainer(@NotNull PyParameterList parameterList) {
    return StreamEx
      .of(parameterList.getParameters())
      .select(PyNamedParameter.class)
      .findFirst(PyNamedParameter::isKeywordContainer)
      .orElse(null);
  }

  @Override
  public void doInvoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
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
        .filter(PyNames::isIdentifier)
        .ifPresent(
          indexValue -> {
            final PyExpression parameter = elementGenerator.createExpressionFromText(LanguageLevel.forElement(function), indexValue);

            insertParameter(function.getParameterList(), parameter, false, elementGenerator);
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
        .filter(PyNames::isIdentifier)
        .ifPresent(
          indexValue -> {
            final PyNamedParameter parameter = createParameter(elementGenerator, call, indexValue);
            if (parameter != null) {
              final PyExpression parameterUsage = elementGenerator.createExpressionFromText(LanguageLevel.forElement(function), indexValue);

              insertParameter(function.getParameterList(), parameter, parameter.hasDefaultValue(), elementGenerator);
              call.replace(parameterUsage);
            }
          }
        );
    }
  }

  @NotNull
  private static <T> List<T> findKeywordContainerUsages(@NotNull PyFunction function,
                                                        @NotNull BiPredicate<PsiElement, PyParameter> usagePredicate) {
    final PyParameter keywordContainer = getKeywordContainer(function.getParameterList());

    if (keywordContainer != null) {
      final List<T> result = new ArrayList<>();
      final Stack<PsiElement> stack = new Stack<>();

      for (PyStatement statement : function.getStatementList().getStatements()) {
        stack.push(statement);

        while (!stack.isEmpty()) {
          final PsiElement element = stack.pop();

          if (usagePredicate.test(element, keywordContainer)) {
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

  private static boolean isKeywordContainerSubscription(@Nullable PsiElement element, @NotNull PyParameter keywordContainer) {
    if (element instanceof PySubscriptionExpression) {
      final PyExpression operand = ((PySubscriptionExpression)element).getOperand();
      if (operand instanceof PyReferenceExpression) {
        return ((PyReferenceExpression)operand).getReference().isReferenceTo(keywordContainer);
      }
    }

    return false;
  }

  private static boolean isKeywordContainerCall(@Nullable PsiElement element, @NotNull PyParameter keywordContainer) {
    if (element instanceof PyCallExpression) {
      final PyExpression callee = ((PyCallExpression)element).getCallee();
      if (callee instanceof PyQualifiedExpression) {
        final PyQualifiedExpression qualifiedCallee = (PyQualifiedExpression)callee;
        final PyExpression qualifier = qualifiedCallee.getQualifier();

        if (qualifier instanceof PyReferenceExpression) {
          return ((PyReferenceExpression)qualifier).getReference().isReferenceTo(keywordContainer) &&
                 ArrayUtil.contains(qualifiedCallee.getReferencedName(), "get", "pop", PyNames.GETITEM);
        }
      }
    }
    return false;
  }

  private static void insertParameter(@NotNull PyParameterList parameterList,
                                      @NotNull PyElement parameter,
                                      boolean hasDefaultValue,
                                      @NotNull PyElementGenerator elementGenerator) {
    final PyElement placeToInsertParameter = findCompatiblePlaceToInsertParameter(parameterList, hasDefaultValue);

    parameterList.addBefore(parameter, placeToInsertParameter);
    parameterList.addBefore((PsiElement)elementGenerator.createComma(), placeToInsertParameter);
  }

  @Nullable
  private static PyNamedParameter createParameter(@NotNull PyElementGenerator elementGenerator,
                                                  @NotNull PyCallExpression call,
                                                  @NotNull String parameterName) {
    final PyExpression[] arguments = call.getArguments();
    if (arguments.length > 1) {
      final PyExpression argument = PyUtil.peelArgument(arguments[1]);
      return argument == null ? null : elementGenerator.createParameter(parameterName + "=" + argument.getText());
    }

    final PyQualifiedExpression callee = PyUtil.as(call.getCallee(), PyQualifiedExpression.class);
    if (callee != null && "get".equals(callee.getReferencedName())) {
      return elementGenerator.createParameter(parameterName + "=" + PyNames.NONE);
    }

    return elementGenerator.createParameter(parameterName);
  }

  @Nullable
  private static PyElement findCompatiblePlaceToInsertParameter(@NotNull PyParameterList parameterList, boolean hasDefaultValue) {
    if (hasDefaultValue) return getKeywordContainer(parameterList);

    final List<PyParameter> parameters = Arrays.asList(parameterList.getParameters());
    for (Pair<PyParameter, PyParameter> currentAndNext : ContainerUtil.zip(parameters, parameters.subList(1, parameters.size()))) {
      final PyParameter current = currentAndNext.getFirst();
      if (current instanceof PyNamedParameter && ((PyNamedParameter)current).isPositionalContainer()) {
        return currentAndNext.getSecond();
      }
    }

    return ContainerUtil.find(parameterList.getParameters(),
                              p -> p.hasDefaultValue() || p instanceof PyNamedParameter && ((PyNamedParameter)p).isKeywordContainer());
  }
}
