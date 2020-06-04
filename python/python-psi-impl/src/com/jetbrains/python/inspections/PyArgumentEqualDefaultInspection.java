// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.inspections.quickfix.RemoveArgumentEqualDefaultQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.pyi.PyiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: catherine
 *
 * Inspection to detect situations, where argument passed to function
 * is equal to default parameter value
 * for instance,
 * dict().get(x, None) --> None is default value for second param in dict().get function
 */
public class PyArgumentEqualDefaultInspection extends PyInspection {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  @Override
  public boolean isEnabledByDefault() {
    return false;
  }

  private static class Visitor extends PyInspectionVisitor {
    Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyCallExpression(final PyCallExpression node) {
      if (node.getParent() instanceof PyDecorator) {
        return;
      }
      final List<PyCallable> callables = node.multiResolveCalleeFunction(getResolveContext());
      if (ContainerUtil.exists(callables, callable -> hasSpecialCasedDefaults(callable, node))) {
        return;
      }
      checkArguments(node, node.getArguments());
    }

    @Override
    public void visitPyDecoratorList(final PyDecoratorList node) {
      PyDecorator[] decorators = node.getDecorators();

      for (PyDecorator decorator: decorators) {
        if (decorator.hasArgumentList()) {
          PyExpression[] arguments = decorator.getArguments();
          checkArguments(decorator, arguments);
        }
      }
    }

    private static boolean hasSpecialCasedDefaults(PyCallable callable, PsiElement anchor) {
      final String name = callable.getName();
      final PyBuiltinCache cache = PyBuiltinCache.getInstance(anchor);
      if ("getattr".equals(name) && cache.isBuiltin(callable)) {
        return true;
      }
      else if ("get".equals(name) || "pop".equals(name)) {
        final PyFunction method = callable.asMethod();
        final PyClassType dictType = cache.getDictType();
        if (method != null && dictType != null && method.getContainingClass() == dictType.getPyClass()) {
          return true;
        }
      }
      return false;
    }

    private void checkArguments(PyCallExpression callExpr, PyExpression[] arguments) {
      final PyCallExpression.PyArgumentsMapping mapping = ContainerUtil.getFirstItem(callExpr.multiMapArguments(getResolveContext()));
      if (mapping == null) return;
      final Set<PyExpression> problemElements = new HashSet<>();
      for (Map.Entry<PyExpression, PyCallableParameter> e : mapping.getMappedParameters().entrySet()) {
        final PyExpression defaultValue = findDefaultValue(mapping, e.getValue());
        if (defaultValue != null) {
          PyExpression key = e.getKey();
          if (key instanceof PyKeywordArgument && ((PyKeywordArgument)key).getValueExpression() != null) {
            key = ((PyKeywordArgument)key).getValueExpression();
          }
          if (isEqual(key, defaultValue)) {
            problemElements.add(e.getKey());
          }
        }
      }
      boolean canDelete = true;
      for (int i = arguments.length-1; i != -1; --i) {
        if (problemElements.contains(arguments[i])) {
          if (canDelete)
            registerProblem(arguments[i], PyPsiBundle.message("INSP.argument.equals.to.default"),
                            new RemoveArgumentEqualDefaultQuickFix(problemElements));
          else
            registerProblem(arguments[i], PyPsiBundle.message("INSP.argument.equals.to.default"));

        }
        else if (!(arguments[i] instanceof PyKeywordArgument)) canDelete = false;
      }
    }

    @Nullable
    private static PyExpression findDefaultValue(@NotNull PyCallExpression.PyArgumentsMapping mapping,
                                                 @NotNull PyCallableParameter parameter) {
      final String name = parameter.getName();
      final PyExpression value = parameter.getDefaultValue();

      if (name == null || !(value instanceof PyNoneLiteralExpression) || !((PyNoneLiteralExpression)value).isEllipsis()) return value;

      final PyCallableType callableType = mapping.getCallableType();
      if (callableType == null) return value;

      final PyCallable callable = callableType.getCallable();
      if (callable == null) return value;

      if (PyiUtil.isInsideStub(callable)) {
        final PyCallable originalCallable = PyiUtil.getOriginalElementOrLeaveAsIs(callable, PyCallable.class);

        final PyNamedParameter originalParameter = originalCallable.getParameterList().findParameterByName(name);
        if (originalParameter != null) return originalParameter.getDefaultValue();
      }

      return value;
    }

    private boolean isEqual(PyExpression key, PyExpression defaultValue) {
      if (isBothInstanceOf(key, defaultValue, PyNumericLiteralExpression.class) ||
          isBothInstanceOf(key, defaultValue, PyPrefixExpression.class) ||
          isBothInstanceOf(key, defaultValue, PyBinaryExpression.class) ||
          isBothInstanceOf(key, defaultValue, PyNoneLiteralExpression.class) ||
          isBothInstanceOf(key, defaultValue, PyBoolLiteralExpression.class)) {
        if (key.getText().equals(defaultValue.getText()))
          return true;
      }
      else if (key instanceof PyStringLiteralExpression && defaultValue instanceof PyStringLiteralExpression) {
        if (((PyStringLiteralExpression)key).getStringValue().equals(((PyStringLiteralExpression)defaultValue).getStringValue()))
          return true;
      }
      else if (key instanceof PyReferenceExpression && PyUtil.isPy2ReservedWord((PyReferenceExpression)key) &&
               key.getText().equals(defaultValue.getText())) {
        return true;
      }
      else {
        PsiReference keyRef = key instanceof PyReferenceExpression
                              ? ((PyReferenceExpression)key).getReference(getResolveContext())
                              : key.getReference();
        PsiReference defRef = defaultValue instanceof PyReferenceExpression
                              ? ((PyReferenceExpression)defaultValue).getReference(getResolveContext())
                              : defaultValue.getReference();
        if (keyRef != null && defRef != null) {
          PsiElement keyResolve = keyRef.resolve();
          PsiElement defResolve = defRef.resolve();
          if (keyResolve != null && keyResolve.equals(defResolve)) {
            return true;
          }
        }
      }
      return false;
    }

    private static boolean isBothInstanceOf(@NotNull final PyExpression key,
                                            @NotNull final PyExpression defaultValue,
                                            @NotNull final Class clazz) {
      return clazz.isInstance(key) && clazz.isInstance(defaultValue);
    }
  }
}
