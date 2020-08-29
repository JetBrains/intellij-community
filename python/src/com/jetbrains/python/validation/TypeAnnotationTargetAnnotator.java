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
package com.jetbrains.python.validation;

import com.intellij.lang.annotation.HighlightSeverity;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class TypeAnnotationTargetAnnotator extends PyAnnotator {
  @Override
  public void visitPyAssignmentStatement(@NotNull PyAssignmentStatement node) {
    if (node.getAnnotation() != null && LanguageLevel.forElement(node).isAtLeast(LanguageLevel.PYTHON36)) {
      if (node.getRawTargets().length > 1) {
        getHolder().newAnnotation(HighlightSeverity.ERROR,
                                  PyBundle.message("ANN.variable.annotation.cannot.be.used.in.assignment.with.multiple.targets")).create();
      }
      final PyExpression target = node.getLeftHandSideExpression();
      if (target != null) {
        checkAnnotationTarget(target);
      }
    }
  }

  @Override
  public void visitPyTypeDeclarationStatement(@NotNull PyTypeDeclarationStatement node) {
    if (node.getAnnotation() != null && LanguageLevel.forElement(node).isAtLeast(LanguageLevel.PYTHON36)) {
      checkAnnotationTarget(node.getTarget());
    }
  }

  private void checkAnnotationTarget(@NotNull PyExpression expression) {
    final PyExpression innerExpr = PyPsiUtils.flattenParens(expression);
    if (innerExpr instanceof PyTupleExpression || innerExpr instanceof PyListLiteralExpression) {
      getHolder().newAnnotation(HighlightSeverity.ERROR,
                                PyBundle.message("ANN.variable.annotation.cannot.be.combined.with.tuple.unpacking")).range(innerExpr).create();
    }
    else if (innerExpr != null && !(innerExpr instanceof PyTargetExpression || innerExpr instanceof PySubscriptionExpression)) {
      getHolder().newAnnotation(HighlightSeverity.ERROR, PyBundle.message("ANN.illegal.target.for.variable.annotation")).range(innerExpr).create();
    }
  }
}
