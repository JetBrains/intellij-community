// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.regexp;

import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.codeInsight.PyInjectionUtil;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
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

  private enum RegexpModule {RE, MRAB_REGEX}

  private static final String FLAGS = "flags";

  private final List<RegexpMethodDescriptor> myDescriptors = new ArrayList<>();

  public PythonRegexpInjector() {
    addMethod("compile");
    addMethod("search");
    addMethod("match");
    addMethod("fullmatch");
    addMethod("split");
    addMethod("splititer");
    addMethod("findall");
    addMethod("finditer");
    addMethod("sub");
    addMethod("subf");
    addMethod("subn");
    addMethod("subfn");
    addMethod("template");
  }

  private void addMethod(@NotNull String name) {
    myDescriptors.add(new RegexpMethodDescriptor(name, 0));
  }

  @Override
  public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
    final PyArgumentList argumentList = PyUtil.as(context.getParent(), PyArgumentList.class);
    if (PyInjectionUtil.getLargestStringLiteral(context) == context && argumentList != null) {
      final PyCallExpression call = PsiTreeUtil.getParentOfType(context, PyCallExpression.class);
      if (call != null && resolvePossibleRegexpCall(call) instanceof PyFunction function) {
        final RegexpModule module = findRegexpModule(function);
        final RegexpMethodDescriptor methodDescriptor = module != null ? findRegexpMethodDescriptor(function) : null;
        if (methodDescriptor != null && methodDescriptor.argIndex == ArrayUtil.indexOf(argumentList.getArguments(), context)) {
          injectRegexpLanguage(registrar, context, module, isVerbose(call, function));
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

  private static void injectRegexpLanguage(@NotNull MultiHostRegistrar registrar,
                                           @NotNull PsiElement context,
                                           @NotNull RegexpModule module,
                                           boolean verbose) {
    final Language language = switch (module) {
      case RE -> verbose ? PythonVerboseRegexpLanguage.INSTANCE : PythonRegexpLanguage.INSTANCE;
      case MRAB_REGEX -> verbose ? PythonMrabVerboseRegexpLanguage.INSTANCE : PythonMrabRegexpLanguage.INSTANCE;
    };
    PyInjectionUtil.registerStringLiteralInjection(context, registrar, language);
  }

  @Override
  public @NotNull List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
    return Arrays.asList(PyStringLiteralExpression.class, PyParenthesizedExpression.class, PyBinaryExpression.class,
                         PyCallExpression.class);
  }

  private static boolean isVerbose(@NotNull PyCallExpression call, @NotNull PyFunction function) {
    final PyExpression keywordFlags = call.getKeywordArgument(FLAGS);
    if (keywordFlags != null) {
      return isVerbose(keywordFlags);
    }
    final PyParameterList parameterList = function.getParameterList();
    final PyNamedParameter flagsParameter = parameterList.findParameterByName(FLAGS);
    final int position = flagsParameter != null ? ArrayUtil.indexOf(parameterList.getParameters(), flagsParameter) : -1;
    final PyExpression[] arguments = call.getArguments();
    return position >= 0 && position < arguments.length && isVerbose(arguments[position]);
  }

  private static boolean isVerbose(@Nullable PyExpression expression) {
    if (expression instanceof PyReferenceExpression) {
      final String flagName = ((PyReferenceExpression)expression).getReferencedName();
      return "VERBOSE".equals(flagName) || "X".equals(flagName);
    }
    if (expression instanceof PyBinaryExpression binaryExpression) {
      return isVerbose(binaryExpression.getLeftExpression()) || isVerbose(binaryExpression.getRightExpression());
    }
    return false;
  }

  /**
   * The {@code regex} module keeps its functions in {@code regex._main} from release 2025.10.23.
   * Earlier releases keep them in {@code regex.regex}.
   */
  private static @Nullable RegexpModule findRegexpModule(@NotNull PyFunction function) {
    if (!(ScopeUtil.getScopeOwner(function) instanceof PyFile)) {
      return null;
    }
    final PsiFile containingFile = function.getContainingFile();
    if (ArrayUtil.contains(containingFile.getName(), "re.py", "re.pyi")) {
      return RegexpModule.RE;
    }
    final QualifiedName qName = QualifiedNameFinder.findCanonicalImportPath(containingFile, null);
    if (qName != null && ArrayUtil.contains(qName.toString(), "regex._main", "regex.regex")) {
      return RegexpModule.MRAB_REGEX;
    }
    return null;
  }

  private @Nullable RegexpMethodDescriptor findRegexpMethodDescriptor(@NotNull PyFunction function) {
    final String functionName = function.getName();
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
