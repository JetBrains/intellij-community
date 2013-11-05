/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.codeInsight.PyInjectionUtil;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
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

  private final List<RegexpMethodDescriptor> myDescriptors = new ArrayList<RegexpMethodDescriptor>();

  public PythonRegexpInjector() {
    addMethod("compile");
    addMethod("search");
    addMethod("match");
    addMethod("split");
    addMethod("findall");
    addMethod("finditer");
    addMethod("sub");
    addMethod("subn");
  }

  private void addMethod(@NotNull String name) {
    myDescriptors.add(new RegexpMethodDescriptor(name, 0));
  }

  @Override
  public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
    final PsiElement contextParent = context.getParent();
    if (PyInjectionUtil.isLargestStringLiteral(context) && contextParent instanceof PyArgumentList) {
      final PyExpression[] args = ((PyArgumentList)contextParent).getArguments();
      int index = ArrayUtil.indexOf(args, context);
      PyCallExpression call = PsiTreeUtil.getParentOfType(context, PyCallExpression.class);
      if (call != null) {
        final PyExpression callee = call.getCallee();
        if (callee instanceof PyReferenceExpression && canBeRegexpCall(callee)) {
          final PsiPolyVariantReference ref = ((PyReferenceExpression)callee).getReference(PyResolveContext.noImplicits());
          if (ref != null) {
            final PsiElement element = ref.resolve();
            if (element != null && PsiTreeUtil.getParentOfType(element, ScopeOwner.class) instanceof PyFile &&
                element.getContainingFile().getName().equals("re.py") && isRegexpMethod(element, index)) {
              final Language language = isVerbose(call) ? PythonVerboseRegexpLanguage.INSTANCE : PythonRegexpLanguage.INSTANCE;
              registrar.startInjecting(language);
              PyInjectionUtil.registerStringLiteralInjection(context, registrar);
              registrar.doneInjecting();
            }
          }
        }
      }
    }
  }

  @NotNull
  @Override
  public List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
    return Arrays.asList(PyStringLiteralExpression.class, PyParenthesizedExpression.class, PyBinaryExpression.class,
                         PyCallExpression.class);
  }

  private static boolean isVerbose(@NotNull PyCallExpression call) {
    PyExpression[] arguments = call.getArguments();
    if (arguments.length <= 1) {
      return false;
    }
    return isVerbose(arguments[arguments.length-1]);
  }

  private static boolean isVerbose(@Nullable PyExpression expr) {
    if (expr instanceof PyKeywordArgument) {
      PyKeywordArgument keywordArgument = (PyKeywordArgument)expr;
      if (!"flags".equals(keywordArgument.getName())) {
        return false;
      }
      return isVerbose(keywordArgument.getValueExpression());
    }
    if (expr instanceof PyReferenceExpression) {
      return "VERBOSE".equals(((PyReferenceExpression)expr).getReferencedName());
    }
    if (expr instanceof PyBinaryExpression) {
      return isVerbose(((PyBinaryExpression)expr).getLeftExpression()) || isVerbose(((PyBinaryExpression)expr).getRightExpression());
    }
    return false;
  }

  private boolean isRegexpMethod(@NotNull PsiElement element, int index) {
    if (!(element instanceof PyFunction)) {
      return false;
    }
    final String name = ((PyFunction)element).getName();
    for (RegexpMethodDescriptor descriptor : myDescriptors) {
      if (descriptor.methodName.equals(name) && descriptor.argIndex == index) {
        return true;
      }
    }
    return false;
  }

  private boolean canBeRegexpCall(@NotNull PyExpression callee) {
    String text = callee.getText();
    for (RegexpMethodDescriptor descriptor : myDescriptors) {
      if (text.endsWith(descriptor.methodName)) {
        return true;
      }
    }
    return false;
  }
}
