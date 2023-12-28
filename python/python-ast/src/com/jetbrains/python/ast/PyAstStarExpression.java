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
package com.jetbrains.python.ast;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public interface PyAstStarExpression extends PyAstExpression {
  @Nullable
  default PyAstExpression getExpression() {
    return PsiTreeUtil.getChildOfType(this, PyAstExpression.class);
  }

  default boolean isAssignmentTarget() {
    return getExpression() instanceof PyAstTargetExpression;
  }

  default boolean isUnpacking() {
    if (isAssignmentTarget()) {
      return false;
    }
    PsiElement parent = getParent();
    boolean isUnpackingInSubscription = parent instanceof PyAstSubscriptionExpression ||
                                        (parent instanceof PyAstSliceItem && parent.getChildren().length == 1); // a[1:2, *x], but not a[1:*x]
    while (parent instanceof PyAstParenthesizedExpression) {
      parent = parent.getParent();
    }
    return parent instanceof PyAstTupleExpression || parent instanceof PyAstListLiteralExpression || parent instanceof PyAstSetLiteralExpression ||
           isUnpackingInSubscription;
  }
}
