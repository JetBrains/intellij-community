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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * User: ktisha
 * See pylint W0232
 */
public final class PyClassHasNoInitInspection extends PyInspection {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
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
      if (PyTypedDictTypeProvider.Companion.isTypingTypedDictInheritor(node, myTypeEvalContext)) return;
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
