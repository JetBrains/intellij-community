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
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.rename.ResolveSnapshotProvider;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Query;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.codeInsight.PyPsiIndexUtil;
import com.jetbrains.python.documentation.docstrings.PyDocstringGenerator;
import com.jetbrains.python.inspections.quickfix.PyChangeSignatureQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.psi.search.PyOverridingMethodsSearch;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.TypeEvalContext;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * User : ktisha
 */

public class PyChangeSignatureUsageProcessor implements ChangeSignatureUsageProcessor {

  @Override
  public UsageInfo[] findUsages(ChangeInfo info) {
    if (info instanceof PyChangeInfo) {
      final PyFunction targetFunction = ((PyChangeInfo)info).getMethod();
      final List<UsageInfo> usages = PyPsiIndexUtil.findUsages(targetFunction, true);
      if (!PyUtil.isInitOrNewMethod(targetFunction)) {
        final Query<PyFunction> search = PyOverridingMethodsSearch.search(targetFunction, true);
        for (PyFunction override : search.findAll()) {
          usages.add(new UsageInfo(override));
          usages.addAll(PyPsiIndexUtil.findUsages(override, true));
        }
      }
      return usages.toArray(UsageInfo.EMPTY_ARRAY);
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
      // Don't modify the call that was the cause of Change Signature invocation
      if (call.getUserData(PyChangeSignatureQuickFix.CHANGE_SIGNATURE_ORIGINAL_CALL) != null) {
        return true;
      }
      final PyArgumentList argumentList = call.getArgumentList();
      if (argumentList != null) {
        final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(element.getProject());
        StringBuilder builder = buildSignature((PyChangeInfo)changeInfo, call);

        final PyExpression newCall;
        if (call instanceof PyDecorator) {
          newCall = elementGenerator.createDecoratorList("@" + builder).getDecorators()[0];
        }
        else {
          newCall = elementGenerator.createExpressionFromText(LanguageLevel.forElement(element), builder.toString());
        }
        call.replace(newCall);

        return true;
      }
    }
    else if (element instanceof PyFunction && element != changeInfo.getMethod()) {
      processFunctionDeclaration((PyChangeInfo)changeInfo, (PyFunction)element);
    }
    return false;
  }

  @NotNull
  private static StringBuilder buildSignature(@NotNull PyChangeInfo changeInfo, @NotNull PyCallExpression call) {
    final PyArgumentList argumentList = call.getArgumentList();
    final PyExpression callee = call.getCallee();
    String name = callee != null ? callee.getText() : changeInfo.getNewName();
    StringBuilder builder = new StringBuilder(name + "(");
    if (argumentList != null) {
      List<String> params = collectParameters(changeInfo, call);
      builder.append(StringUtil.join(params, ","));
    }
    builder.append(")");
    return builder;
  }


  @NotNull
  private static List<String> collectParameters(@NotNull PyChangeInfo changeInfo, @NotNull PyCallExpression call) {
    boolean keywordArgsRequired = false;
    final List<String> newArguments = new ArrayList<>();

    final TypeEvalContext typeEvalContext = TypeEvalContext.userInitiated(call.getProject(), null);
    final PyFunction function = changeInfo.getMethod();

    final List<PyCallableParameter> allOrigParams = function.getParameters(typeEvalContext);
    final PyCallExpression.PyArgumentsMapping mapping = PyCallExpressionHelper.mapArguments(call, function, typeEvalContext);

    MultiMap<Integer, PyExpression> oldParamIndexToArgs = MultiMap.create();
    for (Map.Entry<PyExpression, PyCallableParameter> entry : mapping.getMappedParameters().entrySet()) {
      final PyCallableParameter param = entry.getValue();
      oldParamIndexToArgs.putValue(allOrigParams.indexOf(param), entry.getKey());
    }
    assert oldParamIndexToArgs.keySet().stream().allMatch(index -> index >= 0);

    List<PyParameterInfo> newParamInfos = Arrays.asList(changeInfo.getNewParameters());
    
    final int posVarargIndex = ContainerUtil.indexOf(newParamInfos, info -> isPositionalVarargName(info.getName()));
    final int posOnlyMarkerIndex = ContainerUtil.indexOf(newParamInfos, info -> PySlashParameter.TEXT.equals(info.getName()));
    final boolean posVarargEmpty = posVarargIndex != -1 && oldParamIndexToArgs.get(newParamInfos.get(posVarargIndex).getOldIndex()).isEmpty();
    List<PyExpression> notInsertedVariadicKeywordArgs = ContainerUtil.filter(call.getArguments(), a -> {
      return a instanceof PyStarArgument && ((PyStarArgument)a).isKeyword();
    });
    boolean variadicKeywordArgsUsed = false;
    final int implicitCount = mapping.getImplicitParameters().size();
    for (int paramIndex = implicitCount; paramIndex < newParamInfos.size(); paramIndex++) {
      PyParameterInfo info = newParamInfos.get(paramIndex);
      final String paramName = info.getName();
      final boolean isKeywordVararg = isKeywordVarargName(paramName);
      final boolean isPositionalVararg = isPositionalVarargName(paramName);
      final boolean beforePositionalOnlyMarker = paramIndex < posOnlyMarkerIndex;
      final boolean defaultShouldBeInlined = beforePositionalOnlyMarker &&
                                             ContainerUtil.exists(newParamInfos.subList(paramIndex + 1, posOnlyMarkerIndex),
                                                                  i -> !i.isNew() && !oldParamIndexToArgs.get(i.getOldIndex()).isEmpty());
      if (paramName.equals(PySingleStarParameter.TEXT)) {
        keywordArgsRequired = true;
        continue;
      }
      if (paramName.equals(PySlashParameter.TEXT)) {
        continue;
      }
      final String paramDefault = StringUtil.notNullize(info.getDefaultValue());
      final int oldIndex = info.getOldIndex();
      if (oldIndex < 0) {
        if (info.getDefaultInSignature() && !defaultShouldBeInlined) {
          // If the next argument was passed by position it would match with this new default.
          // Imagine "def f(x, y=None): ..." -> "def f(x, foo=None, y=None): ..." and a call "f(1, 2)"
          keywordArgsRequired = true;
        }
        else if (!isKeywordVararg && !isPositionalVararg) {
          newArguments.add(formatArgument(paramName, paramDefault, keywordArgsRequired));
        }
      }
      else {
        final Collection<PyExpression> existingArgs = oldParamIndexToArgs.get(oldIndex);
        final PyCallableParameter oldParam = allOrigParams.get(oldIndex);
        final boolean usesValueFromVariadic = mapping.getParametersMappedToVariadicKeywordArguments().contains(oldParam);
        variadicKeywordArgsUsed |= usesValueFromVariadic;
        if (!existingArgs.isEmpty()) {
          for (PyExpression arg : existingArgs) {
            PyExpression argValue;
            String argName;
            if (arg instanceof PyKeywordArgument) {
              argValue = ((PyKeywordArgument)arg).getValueExpression();
              argName = StringUtil.notNullize(((PyKeywordArgument)arg).getKeyword());
            }
            else {
              argValue = arg;
              argName = "";
            }
            notInsertedVariadicKeywordArgs.remove(argValue);
            // Keep format of existing keyword arguments unless it's illegal in their new position
            if (!argName.isEmpty() && !(paramIndex < posVarargIndex && !posVarargEmpty) && !beforePositionalOnlyMarker) {
              keywordArgsRequired = true;
            }
            assert !(isPositionalVararg && keywordArgsRequired);
            final String argValueText = argValue != null ? argValue.getText() : "";
            final String newArgumentName = isKeywordVararg ? argName : paramName;
            newArguments.add(formatArgument(newArgumentName, argValueText, keywordArgsRequired));
          }
        }
        // Parameter receives its default value from the signature, all subsequent arguments must use keyword form
        else if (info.getDefaultInSignature() && !defaultShouldBeInlined) {
          keywordArgsRequired = true;
        }
        else if (!isPositionalVararg && !isKeywordVararg && !usesValueFromVariadic) {
          // Existing ordinary parameter with neither a default value in the signature, not a corresponding argument.
          // Most likely its default was propagated from the signature down to calls.
          newArguments.add(formatArgument(paramName, paramDefault, keywordArgsRequired));
        }
      }
      // Keyword-only arguments should follow (Python 3) 
      if (isPositionalVararg) {
        keywordArgsRequired = true;
      }
    }
    if (variadicKeywordArgsUsed) {
      newArguments.addAll(ContainerUtil.map(notInsertedVariadicKeywordArgs, PsiElement::getText));
    }

    return newArguments;
  }

  private static boolean isPositionalVarargName(@NotNull String paramName) {
    return !isKeywordVarargName(paramName) && !paramName.equals(PySingleStarParameter.TEXT) && paramName.startsWith("*");
  }

  private static boolean isKeywordVarargName(@NotNull String paramName) {
    return paramName.startsWith("**");
  }

  @NotNull
  private static String formatArgument(@NotNull String name, @NotNull String value, boolean keywordArgument) {
    if (keywordArgument && !value.startsWith("*")) {
      assert !name.startsWith("*");
      return name + "=" + value;
    }
    else {
      return value;
    }
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
      final Map<PyParameter, String> paramRenames = StreamEx.of(changeInfo.getNewParameters())
                                                            .filter(info -> info.getOldIndex() >= 0 && info.isRenamed())
                                                            .toMap(info -> oldParameters[info.getOldIndex()],
                                                                   info -> StringUtil.trimLeading(info.getName(), '*').trim());
      final Map<PsiElement, String> allRenames = new HashMap<>(paramRenames);
      if (changeInfo.isNameChanged()) {
        allRenames.put(function, changeInfo.getNewName());
      }
      for (Map.Entry<PyParameter, String> entry : paramRenames.entrySet()) {
        final PyParameter oldParameter = entry.getKey();
        final String newName = entry.getValue();
        final UsageInfo[] usages = RenameUtil.findUsages(oldParameter, newName, true, false, allRenames);
        for (UsageInfo info : usages) {
          RenameUtil.rename(info, newName);
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
      if (paramName != null && !names.contains(paramName)) {
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
  public void registerConflictResolvers(List<? super ResolveSnapshotProvider.ResolveSnapshot> snapshots,
                                        @NotNull ResolveSnapshotProvider resolveSnapshotProvider,
                                        UsageInfo[] usages, ChangeInfo changeInfo) {
  }
}
