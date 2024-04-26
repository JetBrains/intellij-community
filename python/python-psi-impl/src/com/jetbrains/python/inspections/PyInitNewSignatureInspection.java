// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PythonUiService;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Detect and report incompatibilities between __new__ and __init__ signatures.
 */
public final class PyInitNewSignatureInspection extends PyInspection {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, PyInspectionVisitor.getContext(session));
  }

  public static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @NotNull TypeEvalContext context) {
      super(holder, context);
    }

    @Override
    public void visitPyFunction(@NotNull PyFunction node) {
      final PyClass cls = PyUtil.turnConstructorIntoClass(node);
      if (cls == null || !cls.isNewStyleClass(myTypeEvalContext)) return;

      final List<PyFunction> complementaryMethods = findComplementaryMethods(cls, node);

      for (PyFunction complementaryMethod : complementaryMethods) {
        if (PyUtil.isSignatureCompatibleTo(complementaryMethod, node, myTypeEvalContext) ||
            PyUtil.isSignatureCompatibleTo(node, complementaryMethod, myTypeEvalContext)) {
          return;
        }
      }

      if (complementaryMethods.size() == 1) {
        registerIncompatibilityProblem(node, PythonUiService.getInstance().createPyChangeSignatureQuickFixForMismatchingMethods(
          node, complementaryMethods.get(0)));
      }
      else if (!complementaryMethods.isEmpty()) {
        registerIncompatibilityProblem(node, null);
      }
    }

    @NotNull
    private List<PyFunction> findComplementaryMethods(@NotNull PyClass cls, @NotNull PyFunction original) {
      final String complementaryName = PyUtil.isNewMethod(original) ? PyNames.INIT : PyNames.NEW;
      final List<PyFunction> complementaryMethods = cls.multiFindMethodByName(complementaryName, true, myTypeEvalContext);

      for (PyFunction complementaryMethod : complementaryMethods) {
        final PyClass complementaryMethodClass = complementaryMethod.getContainingClass();

        if (complementaryMethodClass == null ||
            PyUtil.isObjectClass(complementaryMethodClass) ||
            ContainerUtil.exists(PyInspectionExtension.EP_NAME.getExtensionList(),
                                 extension -> extension.ignoreInitNewSignatures(original, complementaryMethod))) {
          return Collections.emptyList();
        }
      }

      return complementaryMethods;
    }

    private void registerIncompatibilityProblem(@NotNull PyFunction function, @Nullable LocalQuickFix quickFix) {
      final PyParameterList parameterList = function.getParameterList();
      final String message = PyPsiBundle.message(PyUtil.isNewMethod(function) ? "INSP.new.incompatible.to.init"
                                                                              : "INSP.init.incompatible.to.new");
      if (quickFix != null) {
        registerProblem(parameterList, message, quickFix);
      }
      else {
        registerProblem(parameterList, message);
      }
    }
  }
}
