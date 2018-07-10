// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.override.PyOverrideImplementUtil;
import com.jetbrains.python.codeInsight.typing.PyProtocolsKt;
import com.jetbrains.python.inspections.quickfix.PyImplementMethodsQuickFix;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyKnownDecoratorUtil;
import com.jetbrains.python.psi.types.PyClassLikeType;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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

    private Visitor(@NotNull ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyClass(PyClass pyClass) {
      if (isAbstract(pyClass) || PyProtocolsKt.isProtocol(pyClass, myTypeEvalContext)) {
        return;
      }
      final List<PyFunction> toImplement = PyOverrideImplementUtil.getAllSuperAbstractMethods(pyClass, myTypeEvalContext);
      final ASTNode nameNode = pyClass.getNameNode();
      if (!toImplement.isEmpty() && nameNode != null) {
        registerProblem(nameNode.getPsi(),
                        PyBundle.message("INSP.NAME.abstract.class.$0.must.implement", pyClass.getName()),
                        new PyImplementMethodsQuickFix(pyClass, toImplement));
      }
    }

    private boolean isAbstract(@NotNull PyClass pyClass) {
      final PyClassLikeType metaClass = pyClass.getMetaClassType(false, myTypeEvalContext);
      if (metaClass != null && PyNames.ABC_META_CLASS.equals(metaClass.getName())) {
        return true;
      }
      for (PyClassLikeType superClassType : pyClass.getSuperClassTypes(myTypeEvalContext)) {
        if (superClassType != null && "abc.ABC".equals(superClassType.getClassQName())) {
          return true;
        }
      }
      for (PyFunction method : pyClass.getMethods()) {
        if (PyKnownDecoratorUtil.hasAbstractDecorator(method, myTypeEvalContext)) {
          return true;
        }
      }
      return false;
    }
  }
}
