// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.codeInsight.typing.PyTypedDictTypeProvider;
import com.jetbrains.python.inspections.quickfix.AddMethodQuickFix;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * User: ktisha
 * See pylint W0232
 */
@ApiStatus.Internal
public final class PyClassHasNoInitInspection extends PyInspection {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                                 boolean isOnTheFly,
                                                 @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, PyInspectionVisitor.getContext(session));
  }

  private static class Visitor extends PyInspectionVisitor {
    Visitor(@Nullable ProblemsHolder holder,
            @NotNull TypeEvalContext context) {
      super(holder, context);
    }

    @Override
    public void visitPyClass(@NotNull PyClass node) {
      final PyClass outerClass = PsiTreeUtil.getParentOfType(node, PyClass.class);
      assert node != null;
      if (outerClass != null && StringUtil.equalsIgnoreCase("meta", node.getName())) {
        return;
      }
      final List<PyClassLikeType> types = node.getAncestorTypes(myTypeEvalContext);
      if (PyTypedDictTypeProvider.Helper.isTypingTypedDictInheritor(node, myTypeEvalContext)) return;
      for (PyClassLikeType type : types) {
        if (type == null) return;
        final String qName = type.getClassQName();
        if (qName != null && qName.contains(PyNames.TEST_CASE)) return;
        if (!(type instanceof PyClassType)) return;
      }

      final PyFunction init = node.findInitOrNew(true, myTypeEvalContext);
      if (init == null) {
        registerProblem(node.getNameIdentifier(), PyPsiBundle.message("INSP.class.has.no.init"),
                        new AddMethodQuickFix(PyNames.INIT, node.getName(), false));
      }
    }
  }
}
