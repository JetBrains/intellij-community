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
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.inspections.quickfix.DictCreationQuickFix;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexey.Ivanov
 */
public class PyDictCreationInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.dict.creation");
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
    public void visitPyAssignmentStatement(PyAssignmentStatement node) {
      if (node.getAssignedValue() instanceof PyDictLiteralExpression) {
        if (node.getTargets().length != 1) {
          return;
        }
        final PyExpression target = node.getTargets()[0];
        final String name = target.getName();
        if (name == null) {
          return;
        }

        PyStatement statement = PsiTreeUtil.getNextSiblingOfType(node, PyStatement.class);

        while (statement instanceof PyAssignmentStatement) {
          final PyAssignmentStatement assignmentStatement = (PyAssignmentStatement)statement;
          final List<Pair<PyExpression, PyExpression>> targets = getDictTargets(target, name, assignmentStatement);
          if (targets == null)
            return;
          if (!targets.isEmpty()) {
            registerProblem(node, "This dictionary creation could be rewritten as a dictionary literal", new DictCreationQuickFix(node));
            break;
          }
          statement = PsiTreeUtil.getNextSiblingOfType(assignmentStatement, PyStatement.class);
        }
      }
    }
  }

  @Nullable
  public static List<Pair<PyExpression, PyExpression>> getDictTargets(@NotNull final PyExpression target,
                                                                      @NotNull final String name,
                                                                      @NotNull final PyAssignmentStatement assignmentStatement) {
    final List<Pair<PyExpression, PyExpression>> targets = new ArrayList<>();
    for (Pair<PyExpression, PyExpression> targetToValue : assignmentStatement.getTargetsToValuesMapping()) {
      if (targetToValue.first instanceof PySubscriptionExpression) {
        final PySubscriptionExpression subscriptionExpression = (PySubscriptionExpression)targetToValue.first;
        if (name.equals(subscriptionExpression.getOperand().getName()) &&
            subscriptionExpression.getIndexExpression() != null &&
            !referencesTarget(targetToValue.second, target)) {
          targets.add(targetToValue);
        }
      }
      else
        return null;
    }
    return targets;
  }

  private static boolean referencesTarget(@NotNull final PyExpression expression, @NotNull final PsiElement target) {
    final List<PsiElement> refs = new ArrayList<>();
    expression.accept(new PyRecursiveElementVisitor() {
      @Override
      public void visitPyReferenceExpression(PyReferenceExpression node) {
        super.visitPyReferenceExpression(node);
        final PsiPolyVariantReference ref = node.getReference();
        if (ref.isReferenceTo(target)) {
          refs.add(node);
        }
      }
    });
    return !refs.isEmpty();
  }
}
