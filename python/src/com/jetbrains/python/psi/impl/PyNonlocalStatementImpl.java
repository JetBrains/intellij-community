// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyNonlocalStatement;
import com.jetbrains.python.psi.PyTargetExpression;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyNonlocalStatementImpl extends PyElementImpl implements PyNonlocalStatement {
  private static final TokenSet TARGET_EXPRESSION_SET = TokenSet.create(PyElementTypes.TARGET_EXPRESSION);

  public PyNonlocalStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  public void acceptPyVisitor(PyElementVisitor visitor) {
    visitor.visitPyNonlocalStatement(this);
  }

  @NotNull
  @Override
  public PyTargetExpression[] getVariables() {
    return childrenToPsi(TARGET_EXPRESSION_SET, PyTargetExpression.EMPTY_ARRAY);
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    if (ArrayUtil.contains(child.getPsi(), getVariables())) {
      PyPsiUtils.deleteAdjacentCommaWithWhitespaces(this, child.getPsi());
    }
    super.deleteChildInternal(child);
  }
}
