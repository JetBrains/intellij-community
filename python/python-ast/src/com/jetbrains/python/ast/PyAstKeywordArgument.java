// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyTokenTypes;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;


@ApiStatus.Experimental
public interface PyAstKeywordArgument extends PyAstExpression, PsiNamedElement {
  @NonNls
  @Nullable
  default String getKeyword() {
    ASTNode node = getKeywordNode();
    return node != null ? node.getText() : null;
  }

  @Nullable
  default PyAstExpression getValueExpression() {
    return PsiTreeUtil.getChildOfType(this, PyAstExpression.class);
  }

  @Nullable
  default ASTNode getKeywordNode() {
    return getNode().findChildByType(PyTokenTypes.IDENTIFIER);
  }

  @Override
  @Nullable
  default String getName() {
    return getKeyword();
  }
}
