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
package com.jetbrains.python.validation;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.codeInsight.functionTypeComments.psi.PyParameterTypeList;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


final class PyStarAnnotatorVisitor extends PyElementVisitor {
  private final @NotNull PyAnnotationHolder myHolder;

  PyStarAnnotatorVisitor(@NotNull PyAnnotationHolder holder) { myHolder = holder; }

  @Override
  public void visitPyStarExpression(@NotNull PyStarExpression node) {
    super.visitPyStarExpression(node);
    PsiElement parent = node.getParent();
    if (!node.isAssignmentTarget() &&
        !allowedUnpacking(node) &&
        !(parent instanceof PyParameterTypeList) &&
        !(parent instanceof PyTypeParameter) &&
        !(parent instanceof PyAnnotation && isVariadicArg(parent.getParent()))) {
      myHolder.newAnnotation(HighlightSeverity.ERROR, PyPsiBundle.message("ANN.can.t.use.starred.expression.here")).create();
    }
  }

  private static boolean allowedUnpacking(@NotNull PyStarExpression starExpression) {
    if (!starExpression.isUnpacking()) {
      return false;
    }

    // Additional contexts where unpacking is prohibited depending on the language version are covered in CompatibilityVisitor.
    final PsiElement parent = PsiTreeUtil.skipParentsOfType(starExpression, PyParenthesizedExpression.class);
    if (parent instanceof PyTupleExpression) {
      final PsiElement tupleParent = parent.getParent();
      if (tupleParent instanceof PyYieldExpression && ((PyYieldExpression)tupleParent).isDelegating()) {
        return false;
      }
    }
    return true;
  }

  public static boolean isVariadicArg(@Nullable PsiElement parameter) {
    return parameter instanceof PyNamedParameter && (((PyNamedParameter)parameter).isPositionalContainer());
  }
}