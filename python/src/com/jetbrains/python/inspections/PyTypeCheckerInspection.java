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
package com.jetbrains.python.inspections;

import com.google.common.collect.Sets;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.containers.hash.LinkedHashMap;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author vlan
 */
public class PyTypeCheckerInspection extends PyInspection {
  private static final Logger LOG = Logger.getInstance(PyTypeCheckerInspection.class.getName());
  private static Key<Long> TIME_KEY = Key.create("PyTypeCheckerInspection.StartTime");

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    if (LOG.isDebugEnabled()) {
      session.putUserData(TIME_KEY, System.nanoTime());
    }
    return new Visitor(holder, session);
  }

  public static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    // TODO: Visit decorators with arguments
    @Override
    public void visitPyCallExpression(PyCallExpression node) {
      checkCallSite(node);
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
        final String iterableClassName = node.isAsync() ? PyNames.ASYNC_ITERABLE : PyNames.ITERABLE;
        if (type != null && !PyTypeChecker.isUnknown(type) && !PyABCUtil.isSubtype(type, iterableClassName, myTypeEvalContext)) {
          registerProblem(source, String.format("Expected 'collections.%s', got '%s' instead",
                                                iterableClassName, PythonDocumentationProvider.getTypeName(type, myTypeEvalContext)));
        }
      }
    }

    @Override
    public void visitPyReturnStatement(PyReturnStatement node) {
      final PyExpression returnExpr = node.getExpression();
      if (returnExpr != null) {
        ScopeOwner owner = ScopeUtil.getScopeOwner(returnExpr);
        if (owner instanceof PyFunction) {
          final PyFunction function = (PyFunction)owner;
          final PyAnnotation annotation = function.getAnnotation();
          final String typeCommentAnnotation = function.getTypeCommentAnnotation();
          if (annotation != null || typeCommentAnnotation != null) {
            final PyType actual = myTypeEvalContext.getType(returnExpr);
            final PyType expected = myTypeEvalContext.getReturnType(function);
            if (!PyTypeChecker.match(expected, actual, myTypeEvalContext)) {
              final String expectedName = PythonDocumentationProvider.getTypeName(expected, myTypeEvalContext);
              final String actualName = PythonDocumentationProvider.getTypeName(actual, myTypeEvalContext);
              registerProblem(returnExpr, String.format("Expected type '%s', got '%s' instead", expectedName, actualName));
            }
          }
        }
      }
    }

    private void checkCallSite(@Nullable PyCallSiteExpression callSite) {
      final List<PyTypeChecker.AnalyzeCallResults> resultsSet = PyTypeChecker.analyzeCallSite(callSite, myTypeEvalContext);
      final List<Map<PyExpression, Pair<String, ProblemHighlightType>>> problemsSet =
        new ArrayList<>();
      for (PyTypeChecker.AnalyzeCallResults results : resultsSet) {
        problemsSet.add(checkMapping(results.getReceiver(), results.getArguments()));
      }
      if (!problemsSet.isEmpty()) {
        Map<PyExpression, Pair<String, ProblemHighlightType>> minProblems = Collections.min(
          problemsSet,
          (o1, o2) -> o1.size() - o2.size()
        );
        for (Map.Entry<PyExpression, Pair<String, ProblemHighlightType>> entry : minProblems.entrySet()) {
          registerProblem(entry.getKey(), entry.getValue().getFirst(), entry.getValue().getSecond());
        }
      }
    }

    @NotNull
    private Map<PyExpression, Pair<String, ProblemHighlightType>> checkMapping(@Nullable PyExpression receiver,
                                                                               @NotNull Map<PyExpression, PyNamedParameter> mapping) {
      final Map<PyExpression, Pair<String, ProblemHighlightType>> problems =
        new HashMap<>();
      final Map<PyGenericType, PyType> substitutions = new LinkedHashMap<>();
      boolean genericsCollected = false;
      for (Map.Entry<PyExpression, PyNamedParameter> entry : mapping.entrySet()) {
        final PyNamedParameter param = entry.getValue();
        final PyExpression arg = entry.getKey();
        if (param.isPositionalContainer() || param.isKeywordContainer()) {
          continue;
        }
        final PyType paramType = myTypeEvalContext.getType(param);
        if (paramType == null) {
          continue;
        }
        final PyType argType = myTypeEvalContext.getType(arg);
        if (!genericsCollected) {
          substitutions.putAll(PyTypeChecker.unifyReceiver(receiver, myTypeEvalContext));
          genericsCollected = true;
        }
        final Pair<String, ProblemHighlightType> problem = checkTypes(paramType, argType, myTypeEvalContext, substitutions);
        if (problem != null) {
          problems.put(arg, problem);
        }
      }
      return problems;
    }

    @Nullable
    private static Pair<String, ProblemHighlightType> checkTypes(@Nullable PyType expected,
                                                                 @Nullable PyType actual,
                                                                 @NotNull TypeEvalContext context,
                                                                 @NotNull Map<PyGenericType, PyType> substitutions) {
      if (actual != null && expected != null) {
        if (!PyTypeChecker.match(expected, actual, context, substitutions)) {
          final String expectedName = PythonDocumentationProvider.getTypeName(expected, context);
          String quotedExpectedName = String.format("'%s'", expectedName);
          final boolean hasGenerics = PyTypeChecker.hasGenerics(expected, context);
          ProblemHighlightType highlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
          if (hasGenerics) {
            final PyType substitute = PyTypeChecker.substitute(expected, substitutions, context);
            if (substitute != null) {
              quotedExpectedName = String.format("'%s' (matched generic type '%s')",
                                                 PythonDocumentationProvider.getTypeName(substitute, context),
                                                 expectedName);
              highlightType = ProblemHighlightType.WEAK_WARNING;
            }
          }
          final String actualName = PythonDocumentationProvider.getTypeName(actual, context);
          String msg = String.format("Expected type %s, got '%s' instead", quotedExpectedName, actualName);
          if (expected instanceof PyStructuralType) {
            final Set<String> expectedAttributes = ((PyStructuralType)expected).getAttributeNames();
            final Set<String> actualAttributes = getAttributes(actual, context);
            if (actualAttributes != null) {
              final Sets.SetView<String> missingAttributes = Sets.difference(expectedAttributes, actualAttributes);
              if (missingAttributes.size() == 1) {
                msg = String.format("Type '%s' doesn't have expected attribute '%s'", actualName, missingAttributes.iterator().next());
              }
              else {
                msg = String.format("Type '%s' doesn't have expected attributes %s",
                                    actualName,
                                    StringUtil.join(missingAttributes, s -> String.format("'%s'", s), ", "));
              }
            }
          }
          return Pair.create(msg, highlightType);
        }
      }
      return null;
    }
  }

  @Nullable
  private static Set<String> getAttributes(@NotNull PyType type, @NotNull TypeEvalContext context) {
    if (type instanceof PyStructuralType) {
      return ((PyStructuralType)type).getAttributeNames();
    }
    else if (type instanceof PyClassLikeType) {
      return ((PyClassLikeType)type).getMemberNames(true, context);
    }
    return null;
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
