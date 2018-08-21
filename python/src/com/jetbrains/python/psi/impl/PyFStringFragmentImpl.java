// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFStringFragment;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PyFStringFragmentImpl extends PyElementImpl implements PyFStringFragment {
  public PyFStringFragmentImpl(ASTNode astNode) {
    super(astNode);
  }

  @Nullable
  @Override
  public PyExpression getMainExpression() {
    return findChildByClass(PyExpression.class);
  }

  @NotNull
  @Override
  public List<PyFStringFragment> getFormatFragments() {
    return findChildrenByType(PyElementTypes.FSTRING_FRAGMENT);
  }

  @Nullable
  @Override
  public PsiElement getColon() {
    return findChildByType(PyTokenTypes.COLON);
  }

  @Nullable
  @Override
  public PsiElement getClosingBrace() {
    return findChildByType(PyTokenTypes.RBRACE);
  }
}
