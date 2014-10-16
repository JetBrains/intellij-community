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

import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.inspections.quickfix.ReplaceFunctionWithSetLiteralQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: catherine
 *
 * Inspection to find set built-in function and replace it with set literal
 * available if the selected language level supports set literals.
 */
public class PySetFunctionToLiteralInspection extends PyInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.set.function.to.literal");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  private static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyCallExpression(final PyCallExpression node) {
      if (!isAvailable(node)) return;
      PyExpression callee = node.getCallee();
      if (node.isCalleeText(PyNames.SET) && callee != null && PyBuiltinCache.isInBuiltins(callee)) {
        PyExpression[] arguments = node.getArguments();
        if (arguments.length == 1) {
          PyElement[] elements = getSetCallArguments(node);
          if (elements.length != 0)
              registerProblem(node, PyBundle.message("INSP.NAME.set.function.to.literal"),
                                               new ReplaceFunctionWithSetLiteralQuickFix());
        }
      }
    }

    private static boolean isAvailable(PyCallExpression node) {
      final InspectionProfile profile = InspectionProjectProfileManager.getInstance(node.getProject()).getInspectionProfile();
      final InspectionToolWrapper inspectionTool = profile.getInspectionTool("PyCompatibilityInspection", node.getProject());
      if (inspectionTool != null) {
        final InspectionProfileEntry inspection = inspectionTool.getTool();
        if (inspection instanceof PyCompatibilityInspection) {
          final JDOMExternalizableStringList versions = ((PyCompatibilityInspection)inspection).ourVersions;
          for (String s : versions) {
            if (!LanguageLevel.fromPythonVersion(s).supportsSetLiterals()) return false;
          }
        }
      }
      return LanguageLevel.forElement(node).supportsSetLiterals();
    }
  }

  public static PyElement[] getSetCallArguments(PyCallExpression node) {
    PyExpression argument = node.getArguments()[0];
    if (argument instanceof PyStringLiteralExpression) {
      return PyElement.EMPTY_ARRAY;
    }
    if ((argument instanceof PySequenceExpression || (argument instanceof PyParenthesizedExpression &&
                  ((PyParenthesizedExpression)argument).getContainedExpression() instanceof PyTupleExpression))) {

      if (argument instanceof PySequenceExpression)
        return ((PySequenceExpression)argument).getElements();
      PyExpression tuple = ((PyParenthesizedExpression)argument).getContainedExpression();
      if (tuple instanceof PyTupleExpression)
        return ((PyTupleExpression)(tuple)).getElements();
    }
    return PyElement.EMPTY_ARRAY;
  }
}
