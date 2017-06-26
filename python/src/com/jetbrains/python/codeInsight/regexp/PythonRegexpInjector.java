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
package com.jetbrains.python.codeInsight.regexp;

import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.impl.source.tree.injected.MultiHostRegistrarImpl;
import com.intellij.psi.impl.source.tree.injected.Place;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.codeInsight.PyInjectionUtil;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author yole
 */
public class PythonRegexpInjector implements MultiHostInjector {
  private static class RegexpMethodDescriptor {
    @NotNull private final String methodName;
    private final int argIndex;

    private RegexpMethodDescriptor(@NotNull String methodName, int argIndex) {
      this.methodName = methodName;
      this.argIndex = argIndex;
    }
  }

  private final List<RegexpMethodDescriptor> myDescriptors = new ArrayList<>();

  public PythonRegexpInjector() {
    addMethod("compile");
    addMethod("search");
    addMethod("match");
    addMethod("split");
    addMethod("findall");
    addMethod("finditer");
    addMethod("sub");
    addMethod("subn");
    addMethod("fullmatch");
  }

  private void addMethod(@NotNull String name) {
    myDescriptors.add(new RegexpMethodDescriptor(name, 0));
  }

  @Override
  public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
    final PyArgumentList argumentList = PyUtil.as(context.getParent(), PyArgumentList.class);
    if (PyInjectionUtil.getLargestStringLiteral(context) == context && argumentList != null) {
      final PyCallExpression call = PsiTreeUtil.getParentOfType(context, PyCallExpression.class);
      if (call != null) {
        final RegexpMethodDescriptor methodDescriptor = findRegexpMethodDescriptor(resolvePossibleRegexpCall(call));
        if (methodDescriptor != null && methodDescriptor.argIndex == ArrayUtil.indexOf(argumentList.getArguments(), context)) {
          injectRegexpLanguage(registrar, context, isVerbose(call));
        }
      }
    }
  }

  @Nullable
  private PsiElement resolvePossibleRegexpCall(@NotNull PyCallExpression call) {
    final PyExpression callee = call.getCallee();

    if (callee instanceof PyReferenceExpression && canBeRegexpCall(callee)) {
      final PyReferenceExpression referenceExpression = (PyReferenceExpression)callee;
      final TypeEvalContext context = TypeEvalContext.codeAnalysis(call.getProject(), call.getContainingFile());
      return referenceExpression.getReference(PyResolveContext.noImplicits().withTypeEvalContext(context)).resolve();
    }

    return null;
  }

  private static void injectRegexpLanguage(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context, boolean verbose) {
    final Language language = verbose ? PythonVerboseRegexpLanguage.INSTANCE : PythonRegexpLanguage.INSTANCE;
    registrar.startInjecting(language);
    final PyInjectionUtil.InjectionResult result = PyInjectionUtil.registerStringLiteralInjection(context, registrar);
    if (result.isInjected()) {
      registrar.doneInjecting();
      if (!result.isStrict()) {
        final PsiFile file = getInjectedFile(registrar);
        if (file != null) {
          file.putUserData(InjectedLanguageUtil.FRANKENSTEIN_INJECTION, Boolean.TRUE);
        }
      }
    }
  }

  @Nullable
  private static PsiFile getInjectedFile(@NotNull MultiHostRegistrar registrar) {
    // Don't add a dependency on IntelliLang here now, but this injector should become IntelliLang-based in the future
    final List<Pair<Place, PsiFile>> result = ((MultiHostRegistrarImpl)registrar).getResult();
    return result == null || result.isEmpty() ? null : result.get(result.size() - 1).second;
  }

  @NotNull
  @Override
  public List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
    return Arrays.asList(PyStringLiteralExpression.class, PyParenthesizedExpression.class, PyBinaryExpression.class,
                         PyCallExpression.class);
  }

  private static boolean isVerbose(@NotNull PyCallExpression call) {
    final PyExpression[] arguments = call.getArguments();
    return arguments.length > 1 && isVerbose(arguments[arguments.length - 1]);
  }

  private static boolean isVerbose(@Nullable PyExpression expression) {
    if (expression instanceof PyKeywordArgument) {
      final PyKeywordArgument keywordArgument = (PyKeywordArgument)expression;
      return "flags".equals(keywordArgument.getName()) && isVerbose(keywordArgument.getValueExpression());
    }
    if (expression instanceof PyReferenceExpression) {
      final String flagName = ((PyReferenceExpression)expression).getReferencedName();
      return "VERBOSE".equals(flagName) || "X".equals(flagName);
    }
    if (expression instanceof PyBinaryExpression) {
      final PyBinaryExpression binaryExpression = (PyBinaryExpression)expression;
      return isVerbose(binaryExpression.getLeftExpression()) || isVerbose(binaryExpression.getRightExpression());
    }
    return false;
  }

  @Nullable
  private RegexpMethodDescriptor findRegexpMethodDescriptor(@Nullable PsiElement element) {
    if (element == null ||
        !(ScopeUtil.getScopeOwner(element) instanceof PyFile) ||
        !element.getContainingFile().getName().equals("re.py") ||
        !(element instanceof PyFunction)) {
      return null;
    }

    final String functionName = ((PyFunction)element).getName();
    return myDescriptors
      .stream()
      .filter(descriptor -> descriptor.methodName.equals(functionName))
      .findAny()
      .orElse(null);
  }

  private boolean canBeRegexpCall(@NotNull PyExpression callee) {
    final String text = callee.getText();
    return myDescriptors
      .stream()
      .anyMatch(descriptor -> text.endsWith(descriptor.methodName));
  }
}
