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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.documentation.docstrings.PlainDocString;
import com.jetbrains.python.inspections.quickfix.DocstringQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.testing.PythonUnitTestUtil;
import com.jetbrains.python.toolbox.Substring;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Alexey.Ivanov
 */
public class PyDocstringInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.docstring");
  }

  @Override
  public boolean isEnabledByDefault() {
    return false;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  public static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyFile(@NotNull PyFile node) {
      checkDocString(node);
    }

    @Override
    public void visitPyFunction(@NotNull PyFunction node) {
      if (PythonUnitTestUtil.isUnitTestCaseFunction(node)) return;
      final PyClass containingClass = node.getContainingClass();
      if (containingClass != null && PythonUnitTestUtil.isUnitTestCaseClass(containingClass)) return;
      final Property property = node.getProperty();
      if (property != null && (node == property.getSetter().valueOrNull() || node == property.getDeleter().valueOrNull())) {
        return;
      }
      final String name = node.getName();
      if (name != null && !name.startsWith("_")) checkDocString(node);
    }

    @Override
    public void visitPyClass(@NotNull PyClass node) {
      if (PythonUnitTestUtil.isUnitTestCaseClass(node)) return;
      final String name = node.getName();
      if (name == null || name.startsWith("_")) {
        return;
      }
      for (PyInspectionExtension extension : Extensions.getExtensions(PyInspectionExtension.EP_NAME)) {
        if (extension.ignoreMissingDocstring(node)) {
          return;
        }
      }
      checkDocString(node);
    }

    private void checkDocString(@NotNull PyDocStringOwner node) {
      final PyStringLiteralExpression docStringExpression = node.getDocStringExpression();
      if (docStringExpression == null) {
        PsiElement marker = null;
        if (node instanceof PyClass) {
          final ASTNode n = ((PyClass)node).getNameNode();
          if (n != null) marker = n.getPsi();
        }
        else if (node instanceof PyFunction) {
          final ASTNode n = ((PyFunction)node).getNameNode();
          if (n != null) marker = n.getPsi();
        }
        else if (node instanceof PyFile) {
          final TextRange tr = new TextRange(0, 0);
          final ProblemsHolder holder = getHolder();
          if (holder != null) {
            holder.registerProblem(node, tr, PyBundle.message("INSP.no.docstring"));
          }
          return;
        }
        if (marker == null) marker = node;
        if (node instanceof PyFunction || (node instanceof PyClass && ((PyClass)node).findInitOrNew(false, null) != null)) {
          registerProblem(marker, PyBundle.message("INSP.no.docstring"), new DocstringQuickFix(null, null));
        }
        else {
          registerProblem(marker, PyBundle.message("INSP.no.docstring"));
        }
      }
      else {
        final boolean registered = checkParameters(node, docStringExpression);
        if (!registered && StringUtil.isEmptyOrSpaces(docStringExpression.getStringValue())) {
          registerProblem(docStringExpression, PyBundle.message("INSP.empty.docstring"));
        }
      }
    }

    private boolean checkParameters(@NotNull PyDocStringOwner pyDocStringOwner, @NotNull PyStringLiteralExpression node) {
      final String text = node.getText();
      if (text == null) {
        return false;
      }

      final StructuredDocString docString = DocStringUtil.parse(text, node);

      if (docString instanceof PlainDocString) {
        return false;
      }

      if (pyDocStringOwner instanceof PyFunction) {
        final PyParameter[] realParams = ((PyFunction)pyDocStringOwner).getParameterList().getParameters();

        final List<PyNamedParameter> missingParams = getMissingParams(docString, realParams);
        boolean registered = false;
        if (!missingParams.isEmpty()) {
          for (PyNamedParameter param : missingParams) {
            registerProblem(param, "Missing parameter " + param.getName() + " in docstring", new DocstringQuickFix(param, null));
          }
          registered = true;
        }
        final List<Substring> unexpectedParams = getUnexpectedParams(docString, realParams);
        if (!unexpectedParams.isEmpty()) {
          for (Substring param : unexpectedParams) {
            final ProblemsHolder holder = getHolder();

            if (holder != null) {
              holder.registerProblem(node, param.getTextRange(),
                                     "Unexpected parameter " + param + " in docstring",
                                     new DocstringQuickFix(null, param.getValue()));
            }
          }
          registered = true;
        }
        return registered;
      }
      return false;
    }

    @NotNull
    private static List<Substring> getUnexpectedParams(@NotNull StructuredDocString docString, @NotNull PyParameter[] realParams) {
      final Map<String, Substring> unexpected = Maps.newHashMap();

      for (Substring s : docString.getParameterSubstrings()) {
        unexpected.put(s.toString(), s);
      }

      for (PyParameter p : realParams) {
        if (unexpected.containsKey(p.getName())) {
          unexpected.remove(p.getName());
        }
      }
      return Lists.newArrayList(unexpected.values());
    }

    @NotNull
    private static List<PyNamedParameter> getMissingParams(@NotNull StructuredDocString docString, @NotNull PyParameter[] realParams) {
      final List<PyNamedParameter> missing = new ArrayList<PyNamedParameter>();
      final List<String> docStringParameters = docString.getParameters();
      for (PyParameter p : realParams) {
        if (p.isSelf() || !(p instanceof PyNamedParameter)) {
          continue;
        }
        if (!docStringParameters.contains(p.getName())) {
          missing.add((PyNamedParameter)p);
        }
      }
      return missing;
    }
  }
}
