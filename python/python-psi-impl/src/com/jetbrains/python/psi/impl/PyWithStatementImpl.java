// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiListLikeElement;
import com.intellij.psi.PsiNamedElement;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.PyWithItem;
import com.jetbrains.python.psi.PyWithStatement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;


public class PyWithStatementImpl extends PyElementImpl implements PyWithStatement, PsiListLikeElement {
  public PyWithStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(final PyElementVisitor pyVisitor) {
    pyVisitor.visitPyWithStatement(this);
  }

  @Nullable
  public PsiNamedElement getNamedElement(@NotNull final String the_name) {
    return PyUtil.IterHelper.findName(getNamedElements(), the_name);
  }

  @Override
  public PyWithItem[] getWithItems() {
    return childrenToPsi(WITH_ITEM, PyWithItem.EMPTY_ARRAY);
  }

  @Override
  public @NotNull List<? extends PsiElement> getComponents() {
    return Arrays.asList(getWithItems());
  }
}
