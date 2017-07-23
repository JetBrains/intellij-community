/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.inspections.quickfix.PyChangeSignatureQuickFix;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Detect and report incompatibilities between __new__ and __init__ signatures.
 *
 * @author dcheryasov
 */
public class PyInitNewSignatureInspection extends PyInspection {
  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.new.init.signature");
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
    public void visitPyFunction(PyFunction node) {
      final String functionName = node.getName();
      if (!PyNames.NEW.equals(functionName) && !PyNames.INIT.equals(functionName)) return;

      final PyClass cls = node.getContainingClass();
      if (cls == null || !cls.isNewStyleClass(myTypeEvalContext)) return;

      final PyFunction complementaryMethod = findComplementaryMethod(cls, node);
      if (complementaryMethod == null) return;

      if (!PyUtil.isSignatureCompatibleTo(complementaryMethod, node, myTypeEvalContext) &&
          !PyUtil.isSignatureCompatibleTo(node, complementaryMethod, myTypeEvalContext) &&
          node.getContainingFile() == cls.getContainingFile()) {
        registerProblem(node.getParameterList(), PyBundle.message(PyNames.NEW.equals(node.getName()) ? "INSP.new.incompatible.to.init"
                                                                                                     : "INSP.init.incompatible.to.new"),
                        PyChangeSignatureQuickFix.forMismatchingMethods(node, complementaryMethod));
      }
    }

    @Nullable
    private PyFunction findComplementaryMethod(@NotNull PyClass cls, @NotNull PyFunction original) {
      final String complementaryName = PyNames.NEW.equals(original.getName()) ? PyNames.INIT : PyNames.NEW;
      final PyFunction complementaryMethod = cls.findMethodByName(complementaryName, true, myTypeEvalContext);

      if (complementaryMethod == null) return null;

      final PyClass complementaryMethodClass = complementaryMethod.getContainingClass();
      if (complementaryMethodClass == null ||
          PyUtil.isObjectClass(complementaryMethodClass) ||
          ContainerUtil.exists(Extensions.getExtensions(PyInspectionExtension.EP_NAME),
                               extension -> extension.ignoreInitNewSignatures(original, complementaryMethod))) {
        return null;
      }

      return complementaryMethod;
    }
  }
}