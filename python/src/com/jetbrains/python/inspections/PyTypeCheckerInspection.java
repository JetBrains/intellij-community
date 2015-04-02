/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.Function;
import com.intellij.util.containers.hash.LinkedHashMap;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

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
        if (type != null && !PyTypeChecker.isUnknown(type) && !PyABCUtil.isSubtype(type, PyNames.ITERABLE, myTypeEvalContext)) {
          registerProblem(source, String.format("Expected 'collections.Iterable', got '%s' instead",
                                                PythonDocumentationProvider.getTypeName(type, myTypeEvalContext)));
        }
      }
    }

    private void checkCallSite(@Nullable PyCallSiteExpression callSite) {
      final Map<PyGenericType, PyType> substitutions = new LinkedHashMap<PyGenericType, PyType>();
      final PyTypeChecker.AnalyzeCallResults results = PyTypeChecker.analyzeCallSite(callSite, myTypeEvalContext);
      if (results != null) {
        boolean genericsCollected = false;
        for (Map.Entry<PyExpression, PyNamedParameter> entry : results.getArguments().entrySet()) {
          final PyNamedParameter p = entry.getValue();
          final PyExpression key = entry.getKey();
          if (p.isPositionalContainer() || p.isKeywordContainer()) {
            // TODO: Support *args, **kwargs
            continue;
          }
          if (p.hasDefaultValue()) {
            final PyExpression value = p.getDefaultValue();
            final String keyName = key.getName();
            if (value != null && keyName != null && keyName.equals(value.getName()))
              continue;
          }
          final PyType paramType = myTypeEvalContext.getType(p);
          if (paramType == null) {
            continue;
          }
          final PyType argType = myTypeEvalContext.getType(key);
          if (!genericsCollected) {
            substitutions.putAll(PyTypeChecker.unifyReceiver(results.getReceiver(), myTypeEvalContext));
            genericsCollected = true;
          }
          checkTypes(paramType, argType, key, myTypeEvalContext, substitutions);
        }
      }
    }

    @Nullable
    private String checkTypes(@Nullable PyType expected, @Nullable PyType actual, @Nullable PsiElement node,
                              @NotNull TypeEvalContext context, @NotNull Map<PyGenericType, PyType> substitutions) {
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
          String msg= String.format("Expected type %s, got '%s' instead", quotedExpectedName, actualName);
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
                                    StringUtil.join(missingAttributes, new Function<String, String>() {
                                      @Override
                                      public String fun(String s) {
                                        return String.format("'%s'", s);
                                      }
                                    }, ", "));
              }
            }
          }
          registerProblem(node, msg, highlightType);
          return msg;
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
    else if (type instanceof PyClassType) {
      return PyTypeChecker.getClassTypeAttributes((PyClassType)type, true, context);
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
