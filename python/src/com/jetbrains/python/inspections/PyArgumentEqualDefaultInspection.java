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
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.inspections.quickfix.RemoveArgumentEqualDefaultQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.types.PyClassType;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
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
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.argument.equal.default");
  }

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
    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyCallExpression(final PyCallExpression node) {
      PyArgumentList list = node.getArgumentList();
      if (list == null) {
        return;
      }
      Callable func = node.resolveCalleeFunction(resolveWithoutImplicits());
      if (func != null && hasSpecialCasedDefaults(func, node)) {
        return;
      }
      CallArgumentsMapping result = list.analyzeCall(resolveWithoutImplicits());
      checkArguments(result, node.getArguments());
    }

    private static boolean hasSpecialCasedDefaults(Callable callable, PsiElement anchor) {
      final String name = callable.getName();
      final PyBuiltinCache cache = PyBuiltinCache.getInstance(anchor);
      if ("getattr".equals(name) && cache.hasInBuiltins(callable)) {
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

    private void checkArguments(CallArgumentsMapping result, PyExpression[] arguments) {
      Map<PyExpression, PyNamedParameter> mapping = result.getPlainMappedParams();
      Set<PyExpression> problemElements = new HashSet<PyExpression>();
      for (Map.Entry<PyExpression, PyNamedParameter> e : mapping.entrySet()) {
        PyExpression defaultValue = e.getValue().getDefaultValue();
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
            registerProblem(arguments[i], PyBundle.message("INSP.argument.equals.to.default"),
                            new RemoveArgumentEqualDefaultQuickFix(problemElements));
          else
            registerProblem(arguments[i], PyBundle.message("INSP.argument.equals.to.default"));

        }
        else if (!(arguments[i] instanceof PyKeywordArgument)) canDelete = false;
      }
    }

    private boolean isEqual(PyExpression key, PyExpression defaultValue) {
      if (key instanceof PyNumericLiteralExpression && defaultValue instanceof PyNumericLiteralExpression) {
        if (key.getText().equals(defaultValue.getText()))
          return true;
      }
      else if (key instanceof PyStringLiteralExpression && defaultValue instanceof PyStringLiteralExpression) {
        if (((PyStringLiteralExpression)key).getStringValue().equals(((PyStringLiteralExpression)defaultValue).getStringValue()))
          return true;
      }
      else {
        PsiReference keyRef = key instanceof PyReferenceExpression 
                              ? ((PyReferenceExpression) key).getReference(resolveWithoutImplicits())
                              : key.getReference();
        PsiReference defRef = defaultValue instanceof PyReferenceExpression
                              ? ((PyReferenceExpression) defaultValue).getReference(resolveWithoutImplicits())
                              : defaultValue.getReference();
        if (keyRef != null && defRef != null) {
          PsiElement keyResolve = keyRef.resolve();
          PsiElement defResolve = defRef.resolve();
          if (keyResolve != null && keyResolve.equals(defResolve))
            return true;
        }
      }
      return false;
    }
  }
}
