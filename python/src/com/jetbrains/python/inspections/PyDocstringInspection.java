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

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.SuppressIntentionAction;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.documentation.DocStringUtil;
import com.jetbrains.python.inspections.quickfix.DocstringQuickFix;
import com.jetbrains.python.inspections.quickfix.PySuppressInspectionFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.testing.PythonUnitTestUtil;
import com.jetbrains.python.toolbox.Substring;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

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
    public void visitPyFile(PyFile node) {
      checkDocString(node);
    }

    @Override
    public void visitPyFunction(PyFunction node) {
      if (PythonUnitTestUtil.isUnitTestCaseFunction(node)) return;
      final PyClass containingClass = node.getContainingClass();
      if (containingClass != null && PythonUnitTestUtil.isUnitTestCaseClass(containingClass)) return;
      final String name = node.getName();
      if (name != null && !name.startsWith("_")) checkDocString(node);
    }

    @Override
    public void visitPyClass(PyClass node) {
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

    private void checkDocString(PyDocStringOwner node) {
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
          TextRange tr = new TextRange(0, 0);
          ProblemsHolder holder = getHolder();
          if (holder != null) {
            holder.registerProblem(node, tr, PyBundle.message("INSP.no.docstring"));
          }
          return;
        }
        if (marker == null) marker = node;
        if (node instanceof PyFunction || (node instanceof PyClass && ((PyClass)node).findInitOrNew(false) != null)) {
          registerProblem(marker, PyBundle.message("INSP.no.docstring"), new DocstringQuickFix(null, null));
        }
        else {
          registerProblem(marker, PyBundle.message("INSP.no.docstring"));
        }
      }
      else {
        boolean registered = checkParameters(node, docStringExpression);
        if (!registered && StringUtil.isEmptyOrSpaces(docStringExpression.getStringValue())) {
          registerProblem(docStringExpression, PyBundle.message("INSP.empty.docstring"));
        }
      }
    }

    private boolean checkParameters(PyDocStringOwner pyDocStringOwner, PyStringLiteralExpression node) {
      final String text = node.getText();
      if (text == null) {
        return false;
      }

      StructuredDocString docString = DocStringUtil.parse(text);

      if (docString == null) {
        return false;
      }

      List<Substring> docstringParams = docString.getParameterSubstrings();

      if (docstringParams == null) {
        return false;
      }

      if (pyDocStringOwner instanceof PyFunction) {
        PyDecoratorList decoratorList = ((PyFunction)pyDocStringOwner).getDecoratorList();
        boolean isClassMethod = false;
        if (decoratorList != null) {
          isClassMethod = decoratorList.findDecorator(PyNames.CLASSMETHOD) != null;
        }
        PyParameter[] realParams = ((PyFunction)pyDocStringOwner).getParameterList().getParameters();

        List<PyParameter> missingParams = getMissingParams(realParams, docstringParams, isClassMethod);
        boolean registered = false;
        if (!missingParams.isEmpty()) {
          for (PyParameter param : missingParams) {
            registerProblem(param, "Missing parameter " + param.getName() + " in docstring",
                            new DocstringQuickFix(param.getName(), null));
          }
          registered = true;
        }
        List<Substring> unexpectedParams = getUnexpectedParams(docstringParams, realParams, node);
        if (!unexpectedParams.isEmpty()) {
          for (Substring param : unexpectedParams) {
            ProblemsHolder holder = getHolder();

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

    private static List<Substring> getUnexpectedParams(List<Substring> docstringParams,
                                                       PyParameter[] realParams,
                                                       PyStringLiteralExpression node) {
      Map<String, Substring> unexpected = Maps.newHashMap();

      for (Substring s : docstringParams) {
        unexpected.put(s.getValue(), s);
      }

      for (PyParameter p : realParams) {
        if (unexpected.containsKey(p.getName())) {
          unexpected.remove(p.getName());
        }
      }
      return Lists.newArrayList(unexpected.values());
    }

    private static List<PyParameter> getMissingParams(PyParameter[] realParams, List<Substring> docstringParams, boolean isClassMethod) {
      List<PyParameter> missing = new ArrayList<PyParameter>();
      Set<String> params = Sets.newHashSet(Lists.transform(docstringParams, new Function<Substring, String>() {
        @Override
        public String apply(Substring input) {
          return input.getValue();
        }
      }));
      boolean hasMissing = false;
      for (PyParameter p : realParams) {
        String paramText = p.getText();
        if ((!isClassMethod && !paramText.equals(PyNames.CANONICAL_SELF))  && !paramText.equals("*") ||
            (isClassMethod && !paramText.equals("cls"))) {
          if (!params.contains(p.getName())) {
            hasMissing = true;
            missing.add(p);
          }
        }
      }
      return hasMissing ? missing : Collections.<PyParameter>emptyList();
    }
  }

  @Override
  public SuppressIntentionAction[] getSuppressActions(@Nullable PsiElement element) {
    List<SuppressIntentionAction> result = new ArrayList<SuppressIntentionAction>();
    if (element != null) {
      if (PsiTreeUtil.getParentOfType(element, PyFunction.class) != null) {
        result.add(new PySuppressInspectionFix(getShortName().replace("Inspection", ""), "Suppress for function", PyFunction.class));
      }
      if (PsiTreeUtil.getParentOfType(element, PyClass.class) != null) {
        result.add(new PySuppressInspectionFix(getShortName().replace("Inspection", ""), "Suppress for class", PyClass.class));
      }
    }
    return result.toArray(new SuppressIntentionAction[result.size()]);
  }
}
