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

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.inspections.quickfix.PyChangeSignatureQuickFix;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Alexey.Ivanov
 */
public class PyMethodOverridingInspection extends PyInspection {
  @Nls
  @NotNull
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.method.over");
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
    public void visitPyFunction(final PyFunction function) {
      // sanity checks
      PyClass cls = function.getContainingClass();
      if (cls == null) return; // not a method, ignore
      String name = function.getName();
      if (PyNames.INIT.equals(name) || PyNames.NEW.equals(name)) return;  // these are expected to change signature
      // real work
      for (PsiElement psiElement : PySuperMethodsSearch.search(function, myTypeEvalContext)) {
        if (psiElement instanceof PyFunction) {
          final PyFunction baseMethod = (PyFunction)psiElement;
          final PyClass baseClass = baseMethod.getContainingClass();
          if (!PyUtil.isSignatureCompatibleTo(function, baseMethod, myTypeEvalContext)) {
            final String msg = PyBundle.message("INSP.signature.mismatch",
                                                cls.getName() + "." + name + "()",
                                                baseClass != null ? baseClass.getName() : "");
            registerProblem(function.getParameterList(), msg, new PyChangeSignatureQuickFix(true));
          }
        }
      }
    }
  }
}
