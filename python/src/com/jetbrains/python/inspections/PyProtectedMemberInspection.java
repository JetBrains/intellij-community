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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.types.PyModuleType;
import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: ktisha
 *
 * Inspection to detect situations, where
 * protected member (i.e. class member with a name beginning with an underscore)
 * is access outside the class or a descendant of the class where it's defined.
 */
public class PyProtectedMemberInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.protected.member.access");
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
    public void visitPyReferenceExpression(PyReferenceExpression node) {
      final PyExpression qualifier = node.getQualifier();
      if (qualifier == null || PyNames.CANONICAL_SELF.equals(qualifier.getText())) return;
      final String name = node.getName();
      if (name != null && name.startsWith("_") && !name.startsWith("__") && !name.endsWith("__")) {
        final PyClass parentClass = getClassOwner(node);
        if (parentClass != null) {
          final PsiReference reference = node.getReference();
          final PsiElement resolvedExpression = reference.resolve();
          final PyClass resolvedClass = getClassOwner(resolvedExpression);
          if (parentClass.isSubclass(resolvedClass))
            return;

          PyClass outerClass = getClassOwner(parentClass);
          while (outerClass != null) {
            if (outerClass.isSubclass(resolvedClass))
              return;

            outerClass = getClassOwner(outerClass);
          }
        }
        final PyType type = myTypeEvalContext.getType(qualifier);
        if (type instanceof PyModuleType)
          registerProblem(node, PyBundle.message("INSP.protected.member.$0.access.module", name));
        else
          registerProblem(node, PyBundle.message("INSP.protected.member.$0.access", name));
      }
    }

    @Nullable
    private static PyClass getClassOwner(@Nullable PsiElement element) {
      for (ScopeOwner owner = ScopeUtil.getScopeOwner(element); owner != null; owner = ScopeUtil.getScopeOwner(owner)) {
        if (owner instanceof PyClass) {
          return (PyClass)owner;
        }
      }
      return null;
    }
  }
}
