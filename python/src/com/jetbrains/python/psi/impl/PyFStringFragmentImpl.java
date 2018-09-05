// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.ObjectUtils;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFStringFragment;
import com.jetbrains.python.psi.PyFStringFragmentFormatPart;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyFStringFragmentImpl extends PyElementImpl implements PyFStringFragment {
  public PyFStringFragmentImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyFStringFragment(this);
  }

  @Nullable
  @Override
  public PyExpression getExpression() {
    return findChildByClass(PyExpression.class);
  }

  @NotNull
  @Override
  public TextRange getExpressionContentRange() {
    final PsiElement endAnchor = ObjectUtils.coalesce(getTypeConversion(), getFormatPart(), getClosingBrace());
    return TextRange.create(1, endAnchor != null ? endAnchor.getStartOffsetInParent(): getTextLength());
  }

  @Nullable
  @Override
  public PsiElement getTypeConversion() {
    return findChildByType(PyTokenTypes.FSTRING_FRAGMENT_TYPE_CONVERSION);
  }

  @Nullable
  @Override
  public PyFStringFragmentFormatPart getFormatPart() {
    return findChildByClass(PyFStringFragmentFormatPart.class);
  }

  @Nullable
  @Override
  public PsiElement getClosingBrace() {
    return findChildByType(PyTokenTypes.RBRACE);
  }
}
