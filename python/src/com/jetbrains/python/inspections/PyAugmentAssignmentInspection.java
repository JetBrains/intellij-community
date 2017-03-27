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
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.inspections.quickfix.AugmentedAssignmentQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyStructuralType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeChecker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * User: catherine
 *
 * Inspection to detect assignments that can be replaced with augmented assignments.
 */
public class PyAugmentAssignmentInspection extends PyInspection {

  @NotNull
  private static final TokenSet OPERATIONS = TokenSet.create(PyTokenTypes.PLUS, PyTokenTypes.MINUS, PyTokenTypes.MULT,
                                                             PyTokenTypes.FLOORDIV, PyTokenTypes.DIV, PyTokenTypes.PERC, PyTokenTypes.AND,
                                                             PyTokenTypes.OR, PyTokenTypes.XOR, PyTokenTypes.LTLT, PyTokenTypes.GTGT,
                                                             PyTokenTypes.EXP);
  @NotNull
  private static final TokenSet COMMUTATIVE_OPERATIONS =
    TokenSet.create(PyTokenTypes.PLUS, PyTokenTypes.MULT, PyTokenTypes.OR, PyTokenTypes.AND);

  @NotNull
  private static final List<String> SEQUENCE_METHODS = Arrays.asList(PyNames.LEN, PyNames.ITER, PyNames.GETITEM, PyNames.CONTAINS);

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.augment.assignment");
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
    public void visitPyAssignmentStatement(final PyAssignmentStatement node) {
      final PyExpression target = node.getLeftHandSideExpression();
      final PyBinaryExpression value = PyUtil.as(node.getAssignedValue(), PyBinaryExpression.class);

      if (target != null && value != null) {
        final PyExpression leftExpression = value.getLeftExpression();
        final PyExpression rightExpression = PyPsiUtils.flattenParens(value.getRightExpression());

        if (leftExpression == null || rightExpression == null) {
          return;
        }

        final String targetText = target.getText();
        final PyExpression mainOperandExpression;
        final PyExpression otherOperandExpression;
        final boolean changedParts;

        if (targetText.equals(leftExpression.getText())) {
          mainOperandExpression = leftExpression;
          otherOperandExpression = rightExpression;
          changedParts = false;
        }
        else if (targetText.equals(rightExpression.getText())) {
          mainOperandExpression = rightExpression;
          otherOperandExpression = leftExpression;
          changedParts = true;
        }
        else {
          return;
        }

        final PyElementType operator = value.getOperator();
        if (operator != null && assignmentCanBeReplaced(mainOperandExpression, otherOperandExpression, operator, changedParts)) {
          registerProblem(node, "Assignment can be replaced with augmented assignment", new AugmentedAssignmentQuickFix());
        }
      }
    }

    private boolean assignmentCanBeReplaced(@NotNull PyExpression mainOperandExpression,
                                            @NotNull PyExpression otherOperandExpression,
                                            @NotNull PyElementType operator,
                                            boolean changedParts) {
      if (!(mainOperandExpression instanceof PyReferenceExpression || mainOperandExpression instanceof PySubscriptionExpression)) {
        return false;
      }

      if (!changedParts && OPERATIONS.contains(operator) || changedParts && COMMUTATIVE_OPERATIONS.contains(operator)) {
        final PyType otherOperandType = myTypeEvalContext.getType(otherOperandExpression);

        if (!PyTypeChecker.isUnknown(otherOperandType)) {
          if (changedParts) {
            if (hasAnySequenceMethod(otherOperandType, otherOperandExpression)) {
              return false;
            }

            final PyType mainOperandType = myTypeEvalContext.getType(mainOperandExpression);
            if (mainOperandType != null && hasAnySequenceMethod(mainOperandType, mainOperandExpression)) {
              return false;
            }
          }

          return isNumeric(otherOperandType, PyBuiltinCache.getInstance(otherOperandExpression)) ||
                 hasAnySequenceMethod(otherOperandType, otherOperandExpression);
        }
      }

      return false;
    }

    private boolean hasAnySequenceMethod(@NotNull PyType type, @NotNull PyExpression location) {
      if (type instanceof PyStructuralType) {
        final Set<String> attributeNames = ((PyStructuralType)type).getAttributeNames();

        return SEQUENCE_METHODS.stream().anyMatch(attributeNames::contains);
      }

      final PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(myTypeEvalContext);

      return !SEQUENCE_METHODS
        .stream()
        .map(method -> type.resolveMember(method, location, AccessDirection.READ, resolveContext))
        .allMatch(ContainerUtil::isEmpty);
    }

    private boolean isNumeric(@NotNull PyType type, @NotNull PyBuiltinCache cache) {
      return PyTypeChecker.match(cache.getComplexType(), type, myTypeEvalContext);
    }
  }

  @Override
  public boolean isEnabledByDefault() {
    return false;
  }
}
