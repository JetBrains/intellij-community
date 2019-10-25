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
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

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
      if (LanguageLevel.forElement(function).isPython2() && function.getParameterList().hasPositionalContainer()) {
        return false;
      }

      final Usages usages = findKeywordContainerUsages(function, false);

      if (usages.hasMoreReferences) return false;
      if (PsiTreeUtil.isAncestor(function.getParameterList(), element, true) &&
          (!usages.calls.isEmpty() || !usages.subscriptions.isEmpty())) {
        return true;
      }

      for (PyCallExpression call : usages.calls.values()) {
        if (PsiTreeUtil.isAncestor(call, element, true)) {
          return true;
        }
      }

      for (PySubscriptionExpression subscription : usages.subscriptions.values()) {
        if (PsiTreeUtil.isAncestor(subscription, element, true)) {
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
      replaceKeywordContainerUsages(function, project);
    }
  }

  @NotNull
  private static Usages findKeywordContainerUsages(@NotNull PyFunction function, boolean skipReferencesToContainer) {
    final PyParameter keywordContainer = getKeywordContainer(function.getParameterList());
    if (keywordContainer == null) return new Usages(false, new LinkedHashSet<>(), MultiMap.empty(), MultiMap.empty());

    final Set<PyExpression> allReferences = new HashSet<>();
    final Set<PyExpression> referencesAsOperandOrReceiver = new HashSet<>();
    final LinkedHashSet<String> names = new LinkedHashSet<>();
    final MultiMap<String, PyCallExpression> calls = MultiMap.create();
    final MultiMap<String, PySubscriptionExpression> subscriptions = MultiMap.create();

    SyntaxTraverser
      .psiTraverser(function.getStatementList())
      .forEach(
        e -> {
          if (!skipReferencesToContainer &&
              e instanceof PyReferenceExpression &&
              ((PyReferenceExpression)e).getReference().isReferenceTo(keywordContainer)) {
            allReferences.add((PyReferenceExpression)e);
          }
          else if (isKeywordContainerCall(e, keywordContainer)) {
            final PyCallExpression call = (PyCallExpression)e;
            referencesAsOperandOrReceiver.add(call.getReceiver(null));

            final String name = getIndexValueToReplace(call);
            if (name != null) {
              names.add(name);
              calls.putValue(name, call);
            }
          }
          else if (isKeywordContainerSubscription(e, keywordContainer)) {
            final PySubscriptionExpression subscription = (PySubscriptionExpression)e;
            referencesAsOperandOrReceiver.add(subscription.getOperand());

            final String name = getIndexValueToReplace(subscription);
            if (name != null) {
              names.add(name);
              subscriptions.putValue(name, subscription);
            }
          }
        }
      );

    allReferences.removeAll(referencesAsOperandOrReceiver);
    return new Usages(!allReferences.isEmpty(), names, calls, subscriptions);
  }

  @Nullable
  private static String getIndexValueToReplace(@NotNull PySubscriptionExpression subscription) {
    return Optional
      .ofNullable(subscription.getIndexExpression())
      .map(indexExpression -> PyUtil.as(indexExpression, PyStringLiteralExpression.class))
      .map(PyStringLiteralExpression::getStringValue)
      .filter(PyNames::isIdentifier)
      .orElse(null);
  }

  @Nullable
  private static String getIndexValueToReplace(@NotNull PyCallExpression call) {
    return Optional
      .of(call.getArguments())
      .map(ArrayUtil::getFirstElement)
      .map(firstArgument -> PyUtil.as(firstArgument, PyStringLiteralExpression.class))
      .map(PyStringLiteralExpression::getStringValue)
      .filter(PyNames::isIdentifier)
      .orElse(null);
  }

  private static void replaceKeywordContainerUsages(@NotNull PyFunction function, @NotNull Project project) {
    final Usages usages = findKeywordContainerUsages(function, true);

    final MultiMap<String, PySubscriptionExpression> subscriptions = usages.subscriptions;
    final MultiMap<String, PyCallExpression> calls = usages.calls;

    final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    for (String name : usages.names) {
      final Collection<PySubscriptionExpression> currentSubscriptions = subscriptions.get(name);
      final Collection<PyCallExpression> currentCalls = calls.get(name);

      if (!currentSubscriptions.isEmpty()) {
        final PyExpression parameter = elementGenerator.createExpressionFromText(LanguageLevel.forElement(function), name);
        insertParameter(function.getParameterList(), parameter, false, elementGenerator);

        currentSubscriptions.forEach(e -> e.replace(parameter));
        currentCalls.forEach(e -> e.replace(parameter));
      }
      else if (!currentCalls.isEmpty()) {
        final Ref<String> defaultValue = getCommonDefaultKeyValue(currentCalls);
        if (defaultValue == null) return;

        final LanguageLevel languageLevel = LanguageLevel.forElement(function);
        final PyNamedParameter parameter = elementGenerator.createParameter(name, defaultValue.get(), null, languageLevel);
        final PyExpression parameterUsage = elementGenerator.createExpressionFromText(languageLevel, name);

        insertParameter(function.getParameterList(), parameter, parameter.hasDefaultValue(), elementGenerator);
        currentCalls.forEach(e -> e.replace(parameterUsage));
      }
    }
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

  /**
   * @return null when <code>calls</code> is empty or there are different default values.
   */
  @Nullable
  private static Ref<String> getCommonDefaultKeyValue(@NotNull Collection<PyCallExpression> calls) {
    Ref<String> defaultValue = null;

    for (PyCallExpression call : calls) {
      final String currentDefaultValue = getDefaultKeyValue(call);
      if (defaultValue == null) {
        defaultValue = Ref.create(currentDefaultValue);
      }
      else if (!Objects.equals(defaultValue.get(), currentDefaultValue)) {
        return null;
      }
    }

    return defaultValue;
  }

  @Nullable
  private static String getDefaultKeyValue(@NotNull PyCallExpression call) {
    final PyExpression[] arguments = call.getArguments();
    if (arguments.length > 1) {
      final PyExpression argument = PyUtil.peelArgument(arguments[1]);
      return argument == null ? null : argument.getText();
    }

    final PyQualifiedExpression callee = PyUtil.as(call.getCallee(), PyQualifiedExpression.class);
    if (callee != null && "get".equals(callee.getReferencedName())) {
      return PyNames.NONE;
    }

    return null;
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

  private static class Usages {

    private final boolean hasMoreReferences;

    @NotNull
    private final LinkedHashSet<String> names;

    @NotNull
    private final MultiMap<String, PyCallExpression> calls;

    @NotNull
    private final MultiMap<String, PySubscriptionExpression> subscriptions;

    private Usages(boolean hasMoreReferences,
                   @NotNull LinkedHashSet<String> names,
                   @NotNull MultiMap<String, PyCallExpression> calls,
                   @NotNull MultiMap<String, PySubscriptionExpression> subscriptions) {
      this.hasMoreReferences = hasMoreReferences;
      this.names = names;
      this.calls = calls;
      this.subscriptions = subscriptions;
    }
  }
}
