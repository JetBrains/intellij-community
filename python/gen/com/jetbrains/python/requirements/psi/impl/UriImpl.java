// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// This is a generated file. Not intended for manual editing.
package com.jetbrains.python.requirements.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.jetbrains.python.requirements.psi.RequirementsTypes.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.jetbrains.python.requirements.psi.*;

public class UriImpl extends ASTWrapperPsiElement implements Uri {

  public UriImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull Visitor visitor) {
    visitor.visitUri(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof Visitor) accept((Visitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public Fragment getFragment() {
    return findChildByClass(Fragment.class);
  }

  @Override
  @Nullable
  public HierPart getHierPart() {
    return findChildByClass(HierPart.class);
  }

  @Override
  @Nullable
  public Query getQuery() {
    return findChildByClass(Query.class);
  }

  @Override
  @NotNull
  public Scheme getScheme() {
    return findNotNullChildByClass(Scheme.class);
  }

}
