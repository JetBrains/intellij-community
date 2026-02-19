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
  default @NonNls @Nullable String getKeyword() {
    ASTNode node = getKeywordNode();
    return node != null ? node.getText() : null;
  }

  default @Nullable PyAstExpression getValueExpression() {
    return PsiTreeUtil.getChildOfType(this, PyAstExpression.class);
  }

  default @Nullable ASTNode getKeywordNode() {
    return getNode().findChildByType(PyTokenTypes.IDENTIFIER);
  }

  @Override
  default @Nullable String getName() {
    return getKeyword();
  }

  @Override
  default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
    pyVisitor.visitPyKeywordArgument(this);
  }
}
