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
import com.intellij.psi.ResolveResult;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.PyGlobalStatement;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * User: ktisha
 *
 * pylint W0601
 */
public class PyGlobalUndefinedInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.global.undefined");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }


  private static class Visitor extends PyInspectionVisitor {
    Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyGlobalStatement(PyGlobalStatement node) {
      final PyTargetExpression[] globals = node.getGlobals();

      for (PyTargetExpression global : globals) {
        final PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(myTypeEvalContext);
        final List<PsiElement> resolved = ContainerUtil.map(global.getReference(resolveContext).multiResolve(false), ResolveResult::getElement);
        if (resolved.contains(global)) {
          final ScopeOwner globalScopeOwner = ScopeUtil.getScopeOwner(global);
          if (ContainerUtil.all(resolved, element -> globalScopeOwner == ScopeUtil.getScopeOwner(element))) {
            registerProblem(global, PyBundle.message("INSP.NAME.global.$0.undefined", global.getName()));
          }
        }
      }
    }
  }
}
