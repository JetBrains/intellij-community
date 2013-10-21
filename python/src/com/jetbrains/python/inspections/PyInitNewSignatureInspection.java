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
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Detect and report incompatibilities between __new__ and __init__ signatures.
 * @author dcheryasov
 */
public class PyInitNewSignatureInspection extends PyInspection {
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
    public void visitPyClass(PyClass cls) {
      if (! cls.isNewStyleClass()) return; // old-style classes don't know about __new__
      PyFunction init_or_new = cls.findInitOrNew(false); // only local
      final PyBuiltinCache builtins = PyBuiltinCache.getInstance(cls);
      if (init_or_new == null || builtins.hasInBuiltins(init_or_new.getContainingClass())) return; // nothing is overridden
      String the_other_name = PyNames.NEW.equals(init_or_new.getName()) ? PyNames.INIT : PyNames.NEW;
      PyFunction the_other = cls.findMethodByName(the_other_name, true);
      if (the_other == null || builtins.getClass("object") == the_other.getContainingClass()) return;
      if (!PyUtil.isSignatureCompatibleTo(the_other, init_or_new, myTypeEvalContext) &&
          !PyUtil.isSignatureCompatibleTo(init_or_new, the_other, myTypeEvalContext) &&
          init_or_new.getContainingFile() == cls.getContainingFile()
      ) {
        registerProblem(init_or_new.getParameterList(), PyNames.NEW.equals(init_or_new.getName()) ?
                                     PyBundle.message("INSP.new.incompatible.to.init") :
                                     PyBundle.message("INSP.init.incompatible.to.new")
        );
      }
    }
  }

}