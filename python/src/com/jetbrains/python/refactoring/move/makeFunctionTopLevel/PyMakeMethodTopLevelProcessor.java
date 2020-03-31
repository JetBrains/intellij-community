// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.move.makeFunctionTopLevel;

import static com.jetbrains.python.psi.PyUtil.as;

import com.google.common.collect.Lists;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.refactoring.PyRefactoringUtil;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class PyMakeMethodTopLevelProcessor extends PyBaseMakeFunctionTopLevelProcessor {

  private final LinkedHashMap<String, String> myAttributeToParameterName = new LinkedHashMap<>();
  private final MultiMap<String, PyReferenceExpression> myAttributeReferences = MultiMap.create();
  private final Set<PsiElement> myReadsOfSelfParam = new HashSet<>();

  public PyMakeMethodTopLevelProcessor(@NotNull PyFunction targetFunction, @NotNull String destination) {
    super(targetFunction, destination);
  }

  @NotNull
  @Override
  protected String getRefactoringName() {
    return PyBundle.message("refactoring.make.method.top.level.dialog.title");
  }

  @Override
  protected void updateUsages(@NotNull Collection<String> newParamNames, UsageInfo @NotNull [] usages) {
    // Field usages
    for (String attrName : myAttributeReferences.keySet()) {
      final Collection<PyReferenceExpression> reads = myAttributeReferences.get(attrName);
      final String paramName = myAttributeToParameterName.get(attrName);
      if (!attrName.equals(paramName)) {
        for (PyReferenceExpression read : reads) {
          read.replace(myGenerator.createExpressionFromText(LanguageLevel.forElement(read), paramName));
        }
      }
      else {
        for (PyReferenceExpression read : reads) {
          removeQualifier(read);
        }
      }
    }

    // Function usages
    final Collection<String> attrNames = myAttributeToParameterName.keySet();
    for (UsageInfo usage : usages) {
      final PsiElement usageElem = usage.getElement();
      if (usageElem == null) {
        continue;
      }

      if (usageElem instanceof PyReferenceExpression) {
        final PyExpression qualifier = ((PyReferenceExpression)usageElem).getQualifier();
        final PyCallExpression callExpr = as(usageElem.getParent(), PyCallExpression.class);
        if (qualifier != null && callExpr != null && callExpr.getArgumentList() != null) {
          PyExpression instanceExpr = qualifier;
          final PyArgumentList argumentList = callExpr.getArgumentList();

          // Class.method(instance) -> method(instance.attr)
          if (resolvesToClass(qualifier)) {
            final PyExpression[] arguments = argumentList.getArguments();
            if (arguments.length > 0) {
              instanceExpr = arguments[0];
              instanceExpr.delete();
            }
            else {
              // It's not clear how to handle usages like Class.method(), since there is no suitable instance
              instanceExpr = null;
            }
          }

          if (instanceExpr != null) {
            // module.inst.method() -> method(module.inst.foo, module.inst.bar)
            if (isPureReferenceExpression(instanceExpr)) {
              // recursive call inside the method
              if (myReadsOfSelfParam.contains(instanceExpr)) {
                addArguments(argumentList, newParamNames);
              }
              else {
                final String instanceExprText = instanceExpr.getText();
                addArguments(argumentList, ContainerUtil.map(attrNames, attribute -> instanceExprText + "." + attribute));
              }
            }
            // Class().method() -> method(Class().foo)
            else if (newParamNames.size() == 1) {
              addArguments(argumentList, Collections.singleton(instanceExpr.getText() + "." + ContainerUtil.getFirstItem(attrNames)));
            }
            // Class().method() -> inst = Class(); method(inst.foo, inst.bar)
            else if (!newParamNames.isEmpty()) {
              final PyStatement anchor = PsiTreeUtil.getParentOfType(callExpr, PyStatement.class);
              //noinspection ConstantConditions
              final String className = StringUtil.notNullize(myFunction.getContainingClass().getName(), PyNames.OBJECT);
              final String targetName = PyRefactoringUtil.selectUniqueNameFromType(className, usageElem);
              final String assignmentText = targetName + " = " + instanceExpr.getText();
              final PyAssignmentStatement assignment = myGenerator.createFromText(LanguageLevel.forElement(callExpr),
                                                                                  PyAssignmentStatement.class,
                                                                                  assignmentText);
              //noinspection ConstantConditions
              anchor.getParent().addBefore(assignment, anchor);
              addArguments(argumentList, ContainerUtil.map(attrNames, attribute -> targetName + "." + attribute));
            }
          }
        }

        // Will replace/invalidate entire expression
        removeQualifier((PyReferenceExpression)usageElem);
      }
    }
  }

  private boolean resolvesToClass(@NotNull PyExpression qualifier) {
    for (PsiElement element : PyUtil.multiResolveTopPriority(qualifier, myResolveContext)) {
      if (element == myFunction.getContainingClass()) {
        return true;
      }
    }
    return false;
  }

  private static boolean isPureReferenceExpression(@NotNull PyExpression expr) {
    if (!(expr instanceof PyReferenceExpression)) {
      return false;
    }
    final PyExpression qualifier = ((PyReferenceExpression)expr).getQualifier();
    return qualifier == null || isPureReferenceExpression(qualifier);
  }

  @NotNull
  private PyReferenceExpression removeQualifier(@NotNull PyReferenceExpression expr) {
    if (!expr.isQualified()) {
      return expr;
    }
    final PyExpression newExpression = myGenerator.createExpressionFromText(LanguageLevel.forElement(expr), expr.getLastChild().getText());
    return (PyReferenceExpression)expr.replace(newExpression);
  }

  @NotNull
  @Override
  protected PyFunction createNewFunction(@NotNull Collection<String> newParams) {
    final PyFunction copied = (PyFunction)myFunction.copy();
    final PyParameter[] params = copied.getParameterList().getParameters();
    if (params.length > 0) {
      params[0].delete();
    }
    addParameters(copied.getParameterList(), newParams);
    return copied;
  }

  @NotNull
  @Override
  protected List<String> collectNewParameterNames() {
    final Set<String> attributeNames = new LinkedHashSet<>();
    for (ScopeOwner owner : PsiTreeUtil.collectElementsOfType(myFunction, ScopeOwner.class)) {
      final AnalysisResult result =  analyseScope(owner);
      if (!result.nonlocalWritesToEnclosingScope.isEmpty()) {
        throw new IncorrectOperationException(PyBundle.message("refactoring.make.function.top.level.error.nonlocal.writes"));
      }
      if (!result.readsOfSelfParametersFromEnclosingScope.isEmpty()) {
        throw new IncorrectOperationException(PyBundle.message("refactoring.make.function.top.level.error.self.reads"));
      }
      if (!result.readsFromEnclosingScope.isEmpty()) {
        throw new IncorrectOperationException(PyBundle.message("refactoring.make.function.top.level.error.outer.scope.reads"));
      }
      if (!result.writesToSelfParameter.isEmpty()) {
        throw new IncorrectOperationException(PyBundle.message("refactoring.make.function.top.level.error.special.usage.of.self"));
      }
      myReadsOfSelfParam.addAll(result.readsOfSelfParameter);
      for (PsiElement usage : result.readsOfSelfParameter) {
        if (usage.getParent() instanceof PyTargetExpression) {
          throw new IncorrectOperationException(PyBundle.message("refactoring.make.function.top.level.error.attribute.writes"));
        }
        final PyReferenceExpression parentReference = as(usage.getParent(), PyReferenceExpression.class);
        if (parentReference != null) {
          final String attrName = parentReference.getName();
          if (attrName != null && PyUtil.isClassPrivateName(attrName)) {
            throw new IncorrectOperationException(PyBundle.message("refactoring.make.function.top.level.error.private.attributes"));
          }
          if (parentReference.getParent() instanceof PyCallExpression) {
            if (!(Objects.equals(myFunction.getName(), parentReference.getName()))) {
              throw new IncorrectOperationException(PyBundle.message("refactoring.make.function.top.level.error.method.calls"));
            }
            else {
              // do not add method itself to its parameters
              continue;
            }
          }
          attributeNames.add(attrName);
          myAttributeReferences.putValue(attrName, parentReference);
        }
        else {
          throw new IncorrectOperationException(PyBundle.message("refactoring.make.function.top.level.error.special.usage.of.self"));
        }
      }
    }
    for (String name : attributeNames) {
      final Collection<PyReferenceExpression> reads = myAttributeReferences.get(name);
      final PsiElement anchor = ContainerUtil.getFirstItem(reads);
      //noinspection ConstantConditions
      if (!PyRefactoringUtil.isValidNewName(name, anchor)) {
        final String indexedName = PyRefactoringUtil.appendNumberUntilValid(name, anchor, PyRefactoringUtil::isValidNewName);
        myAttributeToParameterName.put(name, indexedName);
      }
      else {
        myAttributeToParameterName.put(name, name);
      }
    }
    return Lists.newArrayList(myAttributeToParameterName.values());
  }
}
