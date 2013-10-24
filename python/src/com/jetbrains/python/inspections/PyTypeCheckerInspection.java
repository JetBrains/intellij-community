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
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.containers.hash.LinkedHashMap;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author vlan
 */
public class PyTypeCheckerInspection extends PyInspection {
  private static final Logger LOG = Logger.getInstance(PyTypeCheckerInspection.class.getName());
  private static Key<Long> TIME_KEY = Key.create("PyTypeCheckerInspection.StartTime");

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    if (LOG.isDebugEnabled())
      session.putUserData(TIME_KEY, System.nanoTime());
    return new Visitor(holder, session);
  }

  public static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    // TODO: Visit decorators with arguments
    @Override
    public void visitPyCallExpression(PyCallExpression node) {
      final PyExpression callee = node.getCallee();
      if (callee instanceof PyQualifiedExpression) {
        checkCallSite((PyQualifiedExpression)callee);
      }
    }

    @Override
    public void visitPyBinaryExpression(PyBinaryExpression node) {
      checkCallSite(node);
    }

    @Override
    public void visitPySubscriptionExpression(PySubscriptionExpression node) {
      // TODO: Support slice PySliceExpressions
      checkCallSite(node);
    }

    @Override
    public void visitPyForStatement(PyForStatement node) {
      final PyExpression source = node.getForPart().getSource();
      if (source != null) {
        final PyType type = myTypeEvalContext.getType(source);
        if (type != null && !PyTypeChecker.isUnknown(type) && !PyABCUtil.isSubtype(type, PyNames.ITERABLE)) {
          registerProblem(source, String.format("Expected 'collections.Iterable', got '%s' instead",
                                                PythonDocumentationProvider.getTypeName(type, myTypeEvalContext)));
        }
      }
    }

    private void checkCallSite(@Nullable PyQualifiedExpression callSite) {
      final Map<PyGenericType, PyType> substitutions = new LinkedHashMap<PyGenericType, PyType>();
      final PyTypeChecker.AnalyzeCallResults results = PyTypeChecker.analyzeCallSite(callSite, myTypeEvalContext);
      if (results != null) {
        boolean genericsCollected = false;
        for (Map.Entry<PyExpression, PyNamedParameter> entry : results.getArguments().entrySet()) {
          final PyNamedParameter p = entry.getValue();
          if (p.isPositionalContainer() || p.isKeywordContainer()) {
            // TODO: Support *args, **kwargs
            continue;
          }
          final PyType paramType = myTypeEvalContext.getType(p);
          if (paramType == null) {
            continue;
          }
          final PyType argType = myTypeEvalContext.getType(entry.getKey());
          if (!genericsCollected) {
            substitutions.putAll(PyTypeChecker.collectCallGenerics(results.getCallable(), results.getReceiver(), myTypeEvalContext));
            genericsCollected = true;
          }
          checkTypes(paramType, argType, entry.getKey(), myTypeEvalContext, substitutions);
        }
      }
    }

    @Nullable
    private String checkTypes(@Nullable PyType superType, @Nullable PyType subType, @Nullable PsiElement node,
                              @NotNull TypeEvalContext context, @NotNull Map<PyGenericType, PyType> substitutions) {
      if (subType != null && superType != null) {
        if (!PyTypeChecker.match(superType, subType, context, substitutions)) {
          final String superName = PythonDocumentationProvider.getTypeName(superType, context);
          String expected = String.format("'%s'", superName);
          final boolean hasGenerics = PyTypeChecker.hasGenerics(superType, context);
          ProblemHighlightType highlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
          if (hasGenerics) {
            final PyType subst = PyTypeChecker.substitute(superType, substitutions, context);
            if (subst != null) {
              expected = String.format("'%s' (matched generic type '%s')",
                                       PythonDocumentationProvider.getTypeName(subst, context),
                                       superName);
              highlightType = ProblemHighlightType.WEAK_WARNING;
            }
          }
          final String msg = String.format("Expected type %s, got '%s' instead",
                                           expected,
                                           PythonDocumentationProvider.getTypeName(subType, context));
          registerProblem(node, msg, highlightType);
          return msg;
        }
      }
      return null;
    }
  }

  @Override
  public void inspectionFinished(@NotNull LocalInspectionToolSession session, @NotNull ProblemsHolder problemsHolder) {
    if (LOG.isDebugEnabled()) {
      final Long startTime = session.getUserData(TIME_KEY);
      if (startTime != null) {
        LOG.debug(String.format("[%d] elapsed time: %d ms\n",
                                Thread.currentThread().getId(),
                                (System.nanoTime() - startTime) / 1000000));
      }
    }
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return "Type checker";
  }
}
