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
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.override.PyOverrideImplementUtil;
import com.jetbrains.python.inspections.quickfix.PyImplementMethodsQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * User: ktisha
 */
public class PyAbstractClassInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.abstract.class");
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
    public void visitPyClass(PyClass pyClass) {
      if (isAbstract(pyClass)) {
        return;
      }
      final Set<PyFunction> toBeImplemented = new HashSet<>();
      final Collection<PyFunction> functions = PyOverrideImplementUtil.getAllSuperFunctions(pyClass, myTypeEvalContext);
      for (PyFunction method : functions) {
        if (isAbstractMethodForClass(method, pyClass)) {
          toBeImplemented.add(method);
        }
      }
      final ASTNode nameNode = pyClass.getNameNode();
      if (!toBeImplemented.isEmpty() && nameNode != null) {
        registerProblem(nameNode.getPsi(),
                        PyBundle.message("INSP.NAME.abstract.class.$0.must.implement", pyClass.getName()),
                        new PyImplementMethodsQuickFix(pyClass, toBeImplemented));
      }
    }

    private boolean isAbstract(@NotNull PyClass pyClass) {
      final PyType metaClass = pyClass.getMetaClassType(myTypeEvalContext);
      if (metaClass instanceof PyClassLikeType && PyNames.ABC_META_CLASS.equals(metaClass.getName())) {
        return true;
      }
      if (metaClass == null) {
        final PyExpression metaClassExpr = as(pyClass.getMetaClassExpression(), PyReferenceExpression.class);
        if (metaClassExpr != null && PyNames.ABC_META_CLASS.equals(metaClassExpr.getName())) {
          return true;
        }
      }
      for (PyFunction method : pyClass.getMethods()) {
        if (PyUtil.isDecoratedAsAbstract(method)) {
          return true;
        }
      }
      return false;
    }

    private static boolean isAbstractMethodForClass(@NotNull PyFunction method, @NotNull PyClass cls) {
      final String methodName = method.getName();
      if (methodName == null ||
          cls.findMethodByName(methodName, false, null) != null ||
          cls.findClassAttribute(methodName, false, null) != null) {
        return false;
      }
      return PyUtil.isDecoratedAsAbstract(method) || PyOverrideImplementUtil.raisesNotImplementedError(method);
    }
  }
}
