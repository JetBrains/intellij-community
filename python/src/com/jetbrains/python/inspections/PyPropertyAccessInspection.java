/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.containers.HashMap;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.inspections.quickfix.PyCreatePropertyQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.toolbox.Maybe;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * Checks that properties are accessed correctly.
 * User: dcheryasov
 */
public class PyPropertyAccessInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.property.access");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  public static class Visitor extends PyInspectionVisitor {
    private final HashMap<Pair<PyClass, String>, Property> myPropertyCache = new HashMap<>();

    public Visitor(@NotNull final ProblemsHolder holder, LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyReferenceExpression(PyReferenceExpression node) {
      super.visitPyReferenceExpression(node);
      checkPropertyExpression(node);
    }

    @Override
    public void visitPyTargetExpression(PyTargetExpression node) {
      super.visitPyTargetExpression(node);
      checkPropertyExpression(node);
    }

    private void checkPropertyExpression(PyQualifiedExpression node) {
      final PyExpression qualifier = node.getQualifier();
      if (qualifier != null) {
        final PyType type = myTypeEvalContext.getType(qualifier);
        if (type instanceof PyClassType) {
          final PyClass cls = ((PyClassType)type).getPyClass();
          final String name = node.getName();
          if (name != null) {
            final Pair<PyClass, String> key = Pair.create(cls, name);
            final Property property;
            if (myPropertyCache.containsKey(key)) {
              property = myPropertyCache.get(key);
            }
            else {
              property = cls.findProperty(name, true, myTypeEvalContext);
            }
            myPropertyCache.put(key, property); // we store nulls, too, to know that a property does not exist
            if (property != null) {
              final AccessDirection dir = AccessDirection.of(node);
              checkAccessor(node, name, dir, property);
              if (dir == AccessDirection.READ) {
                final PsiElement parent = node.getParent();
                if (parent instanceof PyAugAssignmentStatement && ((PyAugAssignmentStatement)parent).getTarget() == node) {
                  checkAccessor(node, name, AccessDirection.WRITE, property);
                }
              }
            }
          }
        }
      }
    }

    private void checkAccessor(PyExpression node, String name, AccessDirection dir, Property property) {
      final Maybe<PyCallable> accessor = property.getByDirection(dir);
      if (accessor.isDefined() && accessor.value() == null) {
        final String message;
        if (dir == AccessDirection.WRITE) {
          message = PyBundle.message("INSP.property.$0.cant.be.set", name);
        }
        else if (dir == AccessDirection.DELETE) {
          message = PyBundle.message("INSP.property.$0.cant.be.deleted", name);
        }
        else {
          message = PyBundle.message("INSP.property.$0.cant.be.read", name);
        }
        registerProblem(node, message, new PyCreatePropertyQuickFix(dir));
      }
    }
  }
}
