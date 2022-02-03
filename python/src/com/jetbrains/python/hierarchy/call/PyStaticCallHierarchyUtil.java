// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.hierarchy.call;

import com.intellij.find.findUsages.FindUsagesHandlerBase;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CommonProcessors;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.findUsages.PyClassFindUsagesHandler;
import com.jetbrains.python.findUsages.PyFunctionFindUsagesHandler;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.psi.types.TypeEvalContext;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author novokrest
 */
public class PyStaticCallHierarchyUtil {
  public static Collection<PsiElement> getCallees(@NotNull PyElement element) {
    final List<PsiElement> callees = new ArrayList<>();

    final PyResolveContext resolveContext =
      PyResolveContext.implicitContext(TypeEvalContext.userInitiated(element.getProject(), element.getContainingFile()));

    final PyRecursiveElementVisitor visitor = new PyRecursiveElementVisitor() {
      @Override
      public void visitPyParameterList(@NotNull PyParameterList node) {
      }

      @Override
      public void visitPyLambdaExpression(@NotNull PyLambdaExpression node) {
      }

      @Override
      public void visitPyFunction(@NotNull PyFunction innerFunction) {
        for (PyParameter parameter : innerFunction.getParameterList().getParameters()) {
          PsiElement defaultValue = parameter.getDefaultValue();
          if (defaultValue != null) {
            defaultValue.accept(this);
          }
        }
      }

      @Override
      public void visitPyCallExpression(@NotNull PyCallExpression node) {
        super.visitPyCallExpression(node);

        StreamEx
          .of(node.multiResolveCalleeFunction(resolveContext))
          .select(PyFunction.class)
          .forEach(callees::add);
      }
    };

    visitor.visitElement(element);

    return callees;
  }

  @NotNull
  public static Collection<PsiElement> getCallers(@NotNull PyElement pyElement) {
    final List<PsiElement> callers = new ArrayList<>();
    final Collection<UsageInfo> usages = findUsages(pyElement);

    for (UsageInfo usage : usages) {
      PsiElement element = usage.getElement();
      if (element == null) {
        continue;
      }

      element = element.getParent();
      while (element instanceof PyParenthesizedExpression) {
        element = element.getParent();
      }

      if (element instanceof PyCallExpression) {
        final PyExpression receiver = ((PyCallExpression)element).getReceiver(null);
        if (receiver instanceof PyCallExpression && ((PyCallExpression)receiver).isCalleeText(PyNames.SUPER)) {
          continue;
        }
        final PsiElement caller = PsiTreeUtil.getParentOfType(element, PyParameterList.class, PyFunction.class);
        if (caller instanceof PyFunction) {
          callers.add(caller);
        }
        else if (caller instanceof PyParameterList) {
          final PsiElement innerFunction = PsiTreeUtil.getParentOfType(caller, PyFunction.class);
          final PsiElement outerFunction = PsiTreeUtil.getParentOfType(innerFunction, PyFunction.class);
          if (innerFunction != null && outerFunction != null) {
            callers.add(outerFunction);
          }
        }
      }
    }

    return callers;
  }

  private static Collection<UsageInfo> findUsages(@NotNull final PsiElement element) {
    final FindUsagesHandlerBase handler = createFindUsageHandler(element);
    if (handler == null) {
      return new ArrayList<>();
    }
    final CommonProcessors.CollectProcessor<UsageInfo> processor = new CommonProcessors.CollectProcessor<>();
    final PsiElement[] psiElements = ArrayUtil.mergeArrays(handler.getPrimaryElements(), handler.getSecondaryElements());
    final FindUsagesOptions options = handler.getFindUsagesOptions(null);
    for (PsiElement psiElement : psiElements) {
      handler.processElementUsages(psiElement, processor, options);
    }
    return processor.getResults();
  }

  /**
   * @see com.jetbrains.python.findUsages.PyFindUsagesHandlerFactory#createFindUsagesHandler(PsiElement, boolean)
   */
  @Nullable
  private static FindUsagesHandlerBase createFindUsageHandler(@NotNull final PsiElement element) {
    if (element instanceof PyFunction) {
      final TypeEvalContext context = TypeEvalContext.userInitiated(element.getProject(), null);
      final Collection<PsiElement> superMethods = PySuperMethodsSearch.search((PyFunction)element, true, context).findAll();
      if (superMethods.size() > 0) {
        final PsiElement next = superMethods.iterator().next();
        if (next instanceof PyFunction && !isInObject((PyFunction)next)) {
          List<PsiElement> allMethods = new ArrayList<>();
          allMethods.add(element);
          allMethods.addAll(superMethods);

          return new PyFunctionFindUsagesHandler(element, allMethods);
        }
      }
      return new PyFunctionFindUsagesHandler(element);
    }
    if (element instanceof PyClass) {
      return new PyClassFindUsagesHandler((PyClass)element);
    }
    return null;
  }

  /**
   * @see com.jetbrains.python.findUsages.PyFindUsagesHandlerFactory#isInObject(PyFunction)
   */
  private static boolean isInObject(PyFunction fun) {
    final PyClass containingClass = fun.getContainingClass();
    if (containingClass == null) {
      return false;
    }
    return PyUtil.isObjectClass(containingClass);
  }
}
