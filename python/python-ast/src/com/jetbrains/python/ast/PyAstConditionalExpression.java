// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;


@ApiStatus.Experimental
public interface PyAstConditionalExpression extends PyAstExpression {
  default PyAstExpression getTruePart() {
    final List<PyAstExpression> expressions = PsiTreeUtil.getChildrenOfTypeAsList(this, PyAstExpression.class);
    return expressions.get(0);
  }

  default PyAstExpression getCondition() {
    final List<PyAstExpression> expressions = PsiTreeUtil.getChildrenOfTypeAsList(this, PyAstExpression.class);
    return expressions.size() > 1 ? expressions.get(1) : null;
  }

  default PyAstExpression getFalsePart() {
    final List<PyAstExpression> expressions = PsiTreeUtil.getChildrenOfTypeAsList(this, PyAstExpression.class);
    return expressions.size() == 3 ? expressions.get(2) : null;
  }
}
