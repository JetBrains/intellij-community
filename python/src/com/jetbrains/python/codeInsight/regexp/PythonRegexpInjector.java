// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.regexp;

import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.psi.PsiElement;
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


public final class PythonRegexpInjector implements MultiHostInjector {
  private static final class RegexpMethodDescriptor {
    private final @NotNull String methodName;
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

  private @Nullable PsiElement resolvePossibleRegexpCall(@NotNull PyCallExpression call) {
    final PyExpression callee = call.getCallee();

    if (callee instanceof PyReferenceExpression referenceExpression && canBeRegexpCall(callee)) {
      final TypeEvalContext context = TypeEvalContext.codeAnalysis(call.getProject(), call.getContainingFile());
      return referenceExpression.getReference(PyResolveContext.defaultContext(context)).resolve();
    }

    return null;
  }

  private static void injectRegexpLanguage(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context, boolean verbose) {
    final Language language = verbose ? PythonVerboseRegexpLanguage.INSTANCE : PythonRegexpLanguage.INSTANCE;
    PyInjectionUtil.registerStringLiteralInjection(context, registrar, language);
  }

  @Override
  public @NotNull List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
    return Arrays.asList(PyStringLiteralExpression.class, PyParenthesizedExpression.class, PyBinaryExpression.class,
                         PyCallExpression.class);
  }

  private static boolean isVerbose(@NotNull PyCallExpression call) {
    final PyExpression[] arguments = call.getArguments();
    return arguments.length > 1 && isVerbose(arguments[arguments.length - 1]);
  }

  private static boolean isVerbose(@Nullable PyExpression expression) {
    if (expression instanceof PyKeywordArgument keywordArgument) {
      return "flags".equals(keywordArgument.getName()) && isVerbose(keywordArgument.getValueExpression());
    }
    if (expression instanceof PyReferenceExpression) {
      final String flagName = ((PyReferenceExpression)expression).getReferencedName();
      return "VERBOSE".equals(flagName) || "X".equals(flagName);
    }
    if (expression instanceof PyBinaryExpression binaryExpression) {
      return isVerbose(binaryExpression.getLeftExpression()) || isVerbose(binaryExpression.getRightExpression());
    }
    return false;
  }

  private @Nullable RegexpMethodDescriptor findRegexpMethodDescriptor(@Nullable PsiElement element) {
    if (element == null ||
        !(ScopeUtil.getScopeOwner(element) instanceof PyFile) ||
        !ArrayUtil.contains(element.getContainingFile().getName(), "re.py", "re.pyi") ||
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
