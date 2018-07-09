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

import com.intellij.psi.PsiElement;
import com.jetbrains.python.codeInsight.functionTypeComments.psi.PyParameterTypeList;
import com.jetbrains.python.psi.PyReturnStatement;
import com.jetbrains.python.psi.PyStarExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.PyYieldExpression;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class StarAnnotator extends PyAnnotator {
  @Override
  public void visitPyStarExpression(PyStarExpression node) {
    super.visitPyStarExpression(node);
    if (!node.isAssignmentTarget() && !allowedUnpacking(node) && !(node.getParent() instanceof PyParameterTypeList)) {
      getHolder().createErrorAnnotation(node, "Can't use starred expression here");
    }
  }

  private static boolean allowedUnpacking(@NotNull PyStarExpression starExpression) {
    if (!starExpression.isUnpacking()) {
      return false;
    }

    final PsiElement parent = starExpression.getParent();
    if (parent instanceof PyTupleExpression && (parent.getParent() instanceof PyReturnStatement ||
                                                parent.getParent() instanceof PyYieldExpression)) {
      return false;
    }
    return true;
  }
}
