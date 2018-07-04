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
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.PyNoneType;
import com.jetbrains.python.psi.types.PyTupleType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * @author Alexey.Ivanov
 */
public class PyTupleAssignmentBalanceInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.incorrect.assignment");
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
      final PyExpression lhsExpression = PyPsiUtils.flattenParens(node.getLeftHandSideExpression());
      final PyExpression assignedValue = node.getAssignedValue();
      if (!(lhsExpression instanceof PyTupleExpression) || assignedValue == null) return;

      final PyExpression[] targets = ((PyTupleExpression)lhsExpression).getElements();
      final int targetsLength = targets.length;

      final int starExpressions = countStarExpressions(targets);
      if (starExpressions > 1) {
        registerProblem(lhsExpression, "Only one starred expression allowed in assignment");
        return;
      }

      final int valuesLength = getActualLength(assignedValue, myTypeEvalContext);
      if (valuesLength == -1) return;

      if (targetsLength > valuesLength + starExpressions) {
        registerProblem(assignedValue, "Need more values to unpack");
      }
      else if (starExpressions == 0 && targetsLength < valuesLength) {
        registerProblem(assignedValue, "Too many values to unpack");
      }
    }

    private static int getActualLength(@NotNull PyExpression assignedValue, @NotNull TypeEvalContext context) {
      if (assignedValue instanceof PySequenceExpression) {
        return ((PySequenceExpression)assignedValue).getElements().length;
      }
      else if (assignedValue instanceof PyStringLiteralExpression) {
        return ((PyStringLiteralExpression)assignedValue).getStringValue().length();
      }
      else if (assignedValue instanceof PyNumericLiteralExpression || assignedValue instanceof PyNoneLiteralExpression) {
        return 1;
      }
      else if (assignedValue instanceof PyCallExpression) {
        final PyCallExpression call = (PyCallExpression)assignedValue;
        if (call.isCalleeText("dict")) {
          return call.getArguments().length;
        }
        else if (call.isCalleeText("tuple")) {
          final PyExpression firstArgument = ArrayUtil.getFirstElement(call.getArguments());
          if (firstArgument instanceof PySequenceExpression) {
            return ((PySequenceExpression)firstArgument).getElements().length;
          }
        }
      }

      final PyType assignedType = context.getType(assignedValue);
      if (assignedType instanceof PyTupleType) {
        return ((PyTupleType)assignedType).getElementCount();
      }
      else if (assignedType instanceof PyNoneType) {
        return 1;
      }

      return -1;
    }

    private static int countStarExpressions(@NotNull PyExpression[] expressions) {
      if (expressions.length != 0 && !LanguageLevel.forElement(expressions[0]).isPython2()) {
        return (int) Arrays
          .stream(expressions)
          .filter(PyStarExpression.class::isInstance)
          .count();
      }

      return 0;
    }
  }
}
