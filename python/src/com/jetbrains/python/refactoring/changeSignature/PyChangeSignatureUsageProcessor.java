/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.refactoring.changeSignature;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.changeSignature.ChangeInfo;
import com.intellij.refactoring.changeSignature.ChangeSignatureUsageProcessor;
import com.intellij.refactoring.changeSignature.ParameterInfo;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.rename.ResolveSnapshotProvider;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Query;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.documentation.docstrings.PyDocstringGenerator;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.search.PyOverridingMethodsSearch;
import com.jetbrains.python.refactoring.PyRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * User : ktisha
 */

public class PyChangeSignatureUsageProcessor implements ChangeSignatureUsageProcessor {

  private boolean useKeywords = false;
  private boolean isMethod = false;
  private boolean isAfterStar = false;

  @Override
  public UsageInfo[] findUsages(ChangeInfo info) {
    if (info instanceof PyChangeInfo) {
      final List<UsageInfo> usages = PyRefactoringUtil.findUsages(((PyChangeInfo)info).getMethod(), true);
      final Query<PyFunction> search = PyOverridingMethodsSearch.search(((PyChangeInfo)info).getMethod(), true);
      final Collection<PyFunction> functions = search.findAll();
      for (PyFunction function : functions) {
        usages.add(new UsageInfo(function));
        usages.addAll(PyRefactoringUtil.findUsages(function, true));
      }
      return usages.toArray(new UsageInfo[usages.size()]);
    }
    return UsageInfo.EMPTY_ARRAY;
  }

  @Nullable
  @Override
  public MultiMap<PsiElement, String> findConflicts(ChangeInfo info, Ref<UsageInfo[]> refUsages) {
    final MultiMap<PsiElement, String> conflicts = new MultiMap<>();
    if (info instanceof PyChangeInfo && info.isNameChanged()) {
      final PyFunction function = ((PyChangeInfo)info).getMethod();
      final PyClass clazz = function.getContainingClass();
      if (clazz != null && clazz.findMethodByName(info.getNewName(), true, null) != null) {
        conflicts.putValue(function, RefactoringBundle.message("method.0.is.already.defined.in.the.1",
                                                               info.getNewName(),
                                                               "class " + clazz.getQualifiedName()));
      }
    }
    return conflicts;
  }

  @Override
  public boolean processUsage(final ChangeInfo changeInfo,
                              UsageInfo usageInfo,
                              boolean beforeMethodChange,
                              final UsageInfo[] usages) {
    if (!isPythonUsage(usageInfo)) return false;
    if (!(changeInfo instanceof PyChangeInfo)) return false;
    if (!beforeMethodChange) return false;
    PsiElement element = usageInfo.getElement();

    if (changeInfo.isNameChanged()) {
      final PsiElement method = changeInfo.getMethod();
      RenameUtil.doRenameGenericNamedElement(method, changeInfo.getNewName(), usages, null);
    }
    if (element == null) return false;
    if (element.getParent() instanceof PyCallExpression) {
      final PyCallExpression call = (PyCallExpression)element.getParent();
      final PyArgumentList argumentList = call.getArgumentList();
      if (argumentList != null) {
        final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(element.getProject());
        StringBuilder builder = buildSignature((PyChangeInfo)changeInfo, call);

        final PyExpression newCall;
        if (call instanceof PyDecorator) {
          newCall = elementGenerator.createDecoratorList("@" + builder.toString()).getDecorators()[0];
        }
        else
          newCall = elementGenerator.createExpressionFromText(LanguageLevel.forElement(element), builder.toString());
        call.replace(newCall);

        return true;
      }
    }
    else if (element instanceof PyFunction) {
      processFunctionDeclaration((PyChangeInfo)changeInfo, (PyFunction)element);
    }
    return false;
  }

  private StringBuilder buildSignature(PyChangeInfo changeInfo, PyCallExpression call) {
    final PyArgumentList argumentList = call.getArgumentList();
    final PyExpression callee = call.getCallee();
    String name = callee != null ? callee.getText() : changeInfo.getNewName();
    StringBuilder builder = new StringBuilder(name + "(");
    if (argumentList != null) {
      final PyParameterInfo[] newParameters = changeInfo.getNewParameters();
      List<String> params = collectParameters(newParameters, argumentList);
      builder.append(StringUtil.join(params, ","));
    }
    builder.append(")");
    return builder;
  }


  private List<String> collectParameters(final PyParameterInfo[] newParameters,
                                                @NotNull final PyArgumentList argumentList) {
    useKeywords = false;
    isMethod = false;
    isAfterStar = false;
    List<String> params = new ArrayList<>();

    int currentIndex = 0;
    final PyExpression[] arguments = argumentList.getArguments();

    for (PyParameterInfo info : newParameters) {
      int oldIndex = calculateOldIndex(info);
      final String parameterName = info.getName();
      if (parameterName.equals(PyNames.CANONICAL_SELF) || parameterName.equals("*")) {
        continue;
      }

      if (parameterName.startsWith("**")) {
        currentIndex = addKwArgs(params, arguments, currentIndex);
      }
      else if (parameterName.startsWith("*")) {
        currentIndex = addPositionalContainer(params, arguments, currentIndex);
      }
      else if (oldIndex < 0) {
        addNewParameter(params, info);
        currentIndex += 1;
      }
      else {
        currentIndex = moveParameter(params, argumentList, info, currentIndex, oldIndex, arguments);
      }
    }
    return params;
  }

  private int calculateOldIndex(ParameterInfo info) {
    if (info.getName().equals(PyNames.CANONICAL_SELF)) {
      isMethod = true;
    }
    if (info.getName().equals("*")) {
      isAfterStar = true;
      useKeywords = true;
    }
    int oldIndex = info.getOldIndex();
    oldIndex = isMethod ? oldIndex - 1 : oldIndex;
    oldIndex = isAfterStar ? oldIndex - 1 : oldIndex;
    return oldIndex;
  }


  private static int addPositionalContainer(List<String> params,
                                            PyExpression[] arguments,
                                            int index) {
    for (int i = index; i != arguments.length; ++i) {
      if (!(arguments[i] instanceof PyKeywordArgument)) {
        params.add(arguments[i].getText());
        index += 1;
      }
    }
    return index;
  }

  private static int addKwArgs(List<String> params, PyExpression[] arguments, int index) {
    for (int i = index; i < arguments.length; ++i) {
      if (arguments[i] instanceof PyKeywordArgument) {
        params.add(arguments[i].getText());
        index += 1;
      }
    }
    return index;
  }

  private void addNewParameter(List<String> params, PyParameterInfo info) {
    if (info.getDefaultInSignature()) {
      useKeywords = true;
    }
    else {
      params.add(useKeywords ? info.getName() + " = " + info.getDefaultValue() : info.getDefaultValue());
    }
  }

  /**
   * @return current index in argument list
   */
  private int moveParameter(List<String> params,
                             PyArgumentList argumentList,
                             PyParameterInfo info,
                             int currentIndex,
                             int oldIndex,
                             PyExpression[] arguments) {
    final String paramName = info.getOldName();
    final PyKeywordArgument keywordArgument = argumentList.getKeywordArgument(paramName);
    if (keywordArgument != null) {
      params.add(keywordArgument.getText());
      useKeywords = true;
      return currentIndex + 1;
    }
    else if (currentIndex < arguments.length) {
      final PyExpression currentParameter = arguments[currentIndex];
      if (currentParameter instanceof PyKeywordArgument && info.isRenamed()) {
        params.add(currentParameter.getText());
      }
      else if (oldIndex < arguments.length && (
        !(info.getDefaultInSignature() && arguments[oldIndex].getText().equals(info.getDefaultValue())) || !(currentParameter instanceof PyKeywordArgument))) {
        return addOldPositionParameter(params, arguments[oldIndex], info, currentIndex);
      }
      else
        return currentIndex;
    }
    else if (oldIndex < arguments.length) {
      return addOldPositionParameter(params, arguments[oldIndex], info, currentIndex);
    }
    else if (!info.getDefaultInSignature()) {
      params.add( useKeywords ? paramName + " = " + info.getDefaultValue()
                              : info.getDefaultValue());
    }
    else {
      useKeywords = true;
      return currentIndex;
    }
    return currentIndex + 1;
  }

  private int addOldPositionParameter(List<String> params,
                                      PyExpression argument,
                                      PyParameterInfo info, int currentIndex) {
    final String paramName = info.getName();
    if (argument instanceof PyKeywordArgument) {
      final PyExpression valueExpression = ((PyKeywordArgument)argument).getValueExpression();

      if (!paramName.equals(argument.getName()) && !StringUtil.isEmptyOrSpaces(info.getDefaultValue())) {
        if (!info.getDefaultInSignature())
          params.add(useKeywords ? info.getName() + " = " + info.getDefaultValue() : info.getDefaultValue());
        else
          return currentIndex;
      }
      else {
        params.add(valueExpression == null ? paramName : paramName + " = " + valueExpression.getText());
        useKeywords = true;
      }
    }
    else {
      params.add(useKeywords && !argument.getText().equals(info.getDefaultValue())? paramName + " = " + argument.getText() : argument.getText());
    }
    return currentIndex + 1;
  }

  private static boolean isPythonUsage(UsageInfo info) {
    final PsiElement element = info.getElement();
    if (element == null) return false;
    return element.getLanguage() == PythonLanguage.getInstance();
  }


  @Override
  public boolean processPrimaryMethod(ChangeInfo changeInfo) {
    if (changeInfo instanceof PyChangeInfo && changeInfo.getLanguage().is(PythonLanguage.getInstance())) {
      final PyChangeInfo pyChangeInfo = (PyChangeInfo)changeInfo;
      processFunctionDeclaration(pyChangeInfo, pyChangeInfo.getMethod());
      return true;
    }
    return false;
  }

  private static void processFunctionDeclaration(@NotNull PyChangeInfo changeInfo, @NotNull PyFunction function) {
    if (changeInfo.isParameterNamesChanged()) {
      final PyParameter[] oldParameters = function.getParameterList().getParameters();
      for (PyParameterInfo paramInfo: changeInfo.getNewParameters()) {
        if (paramInfo.getOldIndex() >= 0 && paramInfo.isRenamed()) {
          final String newName = StringUtil.trimLeading(paramInfo.getName(), '*').trim();
          final UsageInfo[] usages = RenameUtil.findUsages(oldParameters[paramInfo.getOldIndex()], newName, true, false, null);
          for (UsageInfo info : usages) {
            RenameUtil.rename(info, newName);
          }
        }
      }
    }
    if (changeInfo.isParameterSetOrOrderChanged()) {
      fixDoc(changeInfo, function);
      updateParameterList(changeInfo, function);
    }
    if (changeInfo.isNameChanged()) {
      RenameUtil.doRenameGenericNamedElement(function, changeInfo.getNewName(), UsageInfo.EMPTY_ARRAY, null);
    }
  }

  private static void fixDoc(PyChangeInfo changeInfo, @NotNull PyFunction function) {
    PyStringLiteralExpression docStringExpression = function.getDocStringExpression();
    if (docStringExpression == null) return;
    final PyParameterInfo[] parameters = changeInfo.getNewParameters();
    Set<String> names = new HashSet<>();
    for (PyParameterInfo info : parameters) {
      names.add(StringUtil.trimLeading(info.getName(), '*').trim());
    }
    final Module module = ModuleUtilCore.findModuleForPsiElement(function);
    if (module == null) return;
    final PyDocstringGenerator generator = PyDocstringGenerator.forDocStringOwner(function);
    for (PyParameter p : function.getParameterList().getParameters()) {
      final String paramName = p.getName();
      if (!names.contains(paramName) && paramName != null) {
        generator.withoutParam(paramName);
      }
    }
    generator.buildAndInsert();
  }

  private static void updateParameterList(PyChangeInfo changeInfo, PyFunction baseMethod) {
    final PsiElement parameterList = baseMethod.getParameterList();

    final PyParameterInfo[] parameters = changeInfo.getNewParameters();
    final StringBuilder builder = new StringBuilder("def foo(");
    final PyStringLiteralExpression docstring = baseMethod.getDocStringExpression();
    final PyParameter[] oldParameters = baseMethod.getParameterList().getParameters();
    final PyElementGenerator generator = PyElementGenerator.getInstance(baseMethod.getProject());
    final PyDocstringGenerator docStringGenerator = PyDocstringGenerator.forDocStringOwner(baseMethod);
    boolean newParameterInDocString = false;
    for (int i = 0; i < parameters.length; ++i) {
      final PyParameterInfo info = parameters[i];

      final int oldIndex = info.getOldIndex();
      if (i != 0 && oldIndex < oldParameters.length) {
        builder.append(", ");
      }

      if (docstring != null && oldIndex < 0) {
        newParameterInDocString = true;
        docStringGenerator.withParam(info.getName());
      }

      if (oldIndex < oldParameters.length) {
        builder.append(info.getName());
      }
      if (oldIndex >= 0 && oldIndex < oldParameters.length) {
        final PyParameter parameter = oldParameters[oldIndex];
        if (parameter instanceof PyNamedParameter) {
          final PyAnnotation annotation = ((PyNamedParameter)parameter).getAnnotation();
          if (annotation != null) {
            builder.append(annotation.getText());
          }
        }
      }
      final String defaultValue = info.getDefaultValue();
      if (defaultValue != null && info.getDefaultInSignature() && StringUtil.isNotEmpty(defaultValue)) {
        builder.append(" = ").append(defaultValue);
      }
    }
    builder.append("): pass");
    
    if (newParameterInDocString) {
      docStringGenerator.buildAndInsert();
    }

    final PyParameterList newParameterList = generator.createFromText(LanguageLevel.forElement(baseMethod),
                                                                      PyFunction.class,
                                                                      builder.toString()).getParameterList();
    parameterList.replace(newParameterList);
  }

  @Override
  public boolean shouldPreviewUsages(ChangeInfo changeInfo, UsageInfo[] usages) {
    return false;
  }

  @Override
  public boolean setupDefaultValues(ChangeInfo changeInfo, Ref<UsageInfo[]> refUsages, Project project) {
    return true;
  }

  @Override
  public void registerConflictResolvers(List<ResolveSnapshotProvider.ResolveSnapshot> snapshots,
                                        @NotNull ResolveSnapshotProvider resolveSnapshotProvider,
                                        UsageInfo[] usages, ChangeInfo changeInfo) {
  }
}
