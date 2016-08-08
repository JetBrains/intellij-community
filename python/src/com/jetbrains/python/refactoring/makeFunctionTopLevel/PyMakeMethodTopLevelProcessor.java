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
package com.jetbrains.python.refactoring.makeFunctionTopLevel;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.codeInsight.imports.AddImportHelper.ImportPriority;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.refactoring.NameSuggesterUtil;
import com.jetbrains.python.refactoring.introduce.IntroduceValidator;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * @author Mikhail Golubev
 */
public class PyMakeMethodTopLevelProcessor extends PyBaseMakeFunctionTopLevelProcessor {

  private final LinkedHashMap<String, String> myAttributeToParameterName = new LinkedHashMap<>();
  private final MultiMap<String, PyReferenceExpression> myAttributeReferences = MultiMap.create();
  private final Set<PsiElement> myReadsOfSelfParam = new HashSet<>();

  public PyMakeMethodTopLevelProcessor(@NotNull PyFunction targetFunction, @NotNull Editor editor) {
    super(targetFunction, editor);
    // It's easier to debug without preview
    setPreviewUsages(!ApplicationManager.getApplication().isInternal());
  }

  @NotNull
  @Override
  protected String getRefactoringName() {
    return PyBundle.message("refactoring.make.method.top.level");
  }

  @Override
  protected void updateUsages(@NotNull Collection<String> newParamNames, @NotNull UsageInfo[] usages) {
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
          
          // Class.method(instance) -> method(instance)
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
              final String targetName = selectUniqueName(usageElem);
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
        
        final PsiFile usageFile = usage.getFile();
        final PsiFile origFile = myFunction.getContainingFile();
        if (usageFile != origFile) {
          final String funcName = myFunction.getName();
          final String origModuleName = QualifiedNameFinder.findShortestImportableName(origFile, origFile.getVirtualFile());
          if (usageFile != null && origModuleName != null && funcName != null) {
            AddImportHelper.addOrUpdateFromImportStatement(usageFile, origModuleName, funcName, null, ImportPriority.PROJECT, null);
          }
        }

        // Will replace/invalidate entire expression
        removeQualifier((PyReferenceExpression)usageElem);
      }
    }
  }

  @NotNull
  private String selectUniqueName(@NotNull PsiElement scopeAnchor) {
    final PyClass pyClass = myFunction.getContainingClass();
    assert pyClass != null;
    final Collection<String> suggestions;
    if (pyClass.getName() != null) {
      suggestions = NameSuggesterUtil.generateNamesByType(pyClass.getName());
    }
    else {
      suggestions = Collections.singleton("inst");
    }
    for (String name : suggestions) {
      if (isValidName(name, scopeAnchor)) {
        return name;
      }
    }

    //noinspection ConstantConditions
    return appendNumberUntilValid(Iterables.getLast(suggestions), scopeAnchor);
  }

  private static boolean isValidName(@NotNull String name, @NotNull PsiElement scopeAnchor) {
    return !(IntroduceValidator.isDefinedInScope(name, scopeAnchor) || PyNames.isReserved(name));
  }

  @NotNull
  private static String appendNumberUntilValid(@NotNull String name, @NotNull PsiElement scopeAnchor) {
    int counter = 1;
    String candidate = name;
    while (!isValidName(candidate, scopeAnchor)) {
      candidate = name + counter;
      counter++;
    }
    return candidate;
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
            if (!(Comparing.equal(myFunction.getName(), parentReference.getName()))) {
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
      if (!isValidName(name, anchor)) {
        final String indexedName = appendNumberUntilValid(name, anchor);
        myAttributeToParameterName.put(name, indexedName);
      }
      else {
        myAttributeToParameterName.put(name, name);
      }
    }
    return Lists.newArrayList(myAttributeToParameterName.values());
  }
}
