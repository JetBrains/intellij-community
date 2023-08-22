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
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.inspections.quickfix.PyChangeBaseClassQuickFix;
import com.jetbrains.python.inspections.quickfix.PyConvertToNewStyleQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * User: catherine
 *
 * Inspection to detect occurrences of new-style class features in old-style classes
 */
public class PyOldStyleClassesInspection extends PyInspection {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, PyInspectionVisitor.getContext(session));
  }

  private static class Visitor extends PyInspectionVisitor {
    Visitor(@Nullable ProblemsHolder holder, @NotNull TypeEvalContext context) {
      super(holder, context);
    }
    @Override
    public void visitPyClass(final @NotNull PyClass node) {
      final List<PyClassLikeType> expressions = node.getSuperClassTypes(myTypeEvalContext);
      List<LocalQuickFix> quickFixes = new ArrayList<>();
      quickFixes.add(new PyConvertToNewStyleQuickFix());
      if (!expressions.isEmpty()) {
        quickFixes.add(new PyChangeBaseClassQuickFix());
      }
      if (!node.isNewStyleClass(myTypeEvalContext)) {
        for (PyTargetExpression attr : node.getClassAttributes()) {
          if (PyNames.SLOTS.equals(attr.getName())) {
            registerProblem(attr, PyPsiBundle.message("INSP.oldstyle.class.slots"), ProblemHighlightType.GENERIC_ERROR_OR_WARNING, null, quickFixes.toArray(
              LocalQuickFix.EMPTY_ARRAY));
          }
        }
        for (PyFunction attr : node.getMethods()) {
          if (PyNames.GETATTRIBUTE.equals(attr.getName())) {
            final ASTNode nameNode = attr.getNameNode();
            assert nameNode != null;
            registerProblem(nameNode.getPsi(), PyPsiBundle.message("INSP.oldstyle.class.getattribute"), ProblemHighlightType.GENERIC_ERROR_OR_WARNING, null, quickFixes.toArray(
              LocalQuickFix.EMPTY_ARRAY));
          }
        }
      }
    }

    @Override
    public void visitPyCallExpression(final @NotNull PyCallExpression node) {
      PyClass klass = PsiTreeUtil.getParentOfType(node, PyClass.class);
      if (klass != null && !klass.isNewStyleClass(myTypeEvalContext)) {
        final List<PyClassLikeType> types = klass.getSuperClassTypes(myTypeEvalContext);
        for (PyClassLikeType type : types) {
          if (type == null) return;
          final String qName = type.getClassQName();
          if (qName != null && qName.contains("PyQt")) return;
          if (!(type instanceof PyClassType)) return;
        }
        List<LocalQuickFix> quickFixes = new ArrayList<>();
        quickFixes.add(new PyConvertToNewStyleQuickFix());
        if (!types.isEmpty()) {
          quickFixes.add(new PyChangeBaseClassQuickFix());
        }

        if (PyUtil.isSuperCall(node)) {
          final PyExpression callee = node.getCallee();
          if (callee != null) {
            registerProblem(callee, PyPsiBundle.message("INSP.oldstyle.class.super"), ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                            null, quickFixes.toArray(LocalQuickFix.EMPTY_ARRAY));
          }
        }
      }
    }
  }
}
