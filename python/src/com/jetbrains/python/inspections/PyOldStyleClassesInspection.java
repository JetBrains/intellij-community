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
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * User: catherine
 *
 * Inspection to detect occurrences of new-style class features in old-style classes
 */
public class PyOldStyleClassesInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.oldstyle.class");
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
    public void visitPyClass(final PyClass node) {
      if (!node.isNewStyleClass()) {
        for (PyTargetExpression attr : node.getClassAttributes()) {
          if ("__slots__".equals(attr.getName())) {
            registerProblem(attr, "Old-style class contains __slots__ definition");
          }
        }
        for (PyFunction attr : node.getMethods()) {
          if ("__getattribute__".equals(attr.getName())) {
            registerProblem(attr, "Old-style class contains __getattribute__ definition");
          }
        }
      }
    }

    @Override
    public void visitPyCallExpression(final PyCallExpression node) {
      PyClass klass = PsiTreeUtil.getParentOfType(node, PyClass.class);
      if (klass != null && !klass.isNewStyleClass()) {
        final List<PyClassLikeType> types = klass.getSuperClassTypes(myTypeEvalContext);
        for (PyClassLikeType type : types) {
          if (type == null) return;
          final String qName = type.getClassQName();
          if (qName != null && qName.contains("PyQt")) return;
          if (!(type instanceof PyClassType)) return;
        }

        if (PyUtil.isSuperCall(node))
          registerProblem(node.getCallee(), "Old-style class contains call for super method");
      }
    }
  }
}
