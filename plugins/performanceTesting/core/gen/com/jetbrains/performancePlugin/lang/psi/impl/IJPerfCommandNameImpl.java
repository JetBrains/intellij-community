// This is a generated file. Not intended for manual editing.
package com.jetbrains.performancePlugin.lang.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.jetbrains.performancePlugin.lang.psi.IJPerfElementTypes.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.jetbrains.performancePlugin.lang.psi.*;

public class IJPerfCommandNameImpl extends ASTWrapperPsiElement implements IJPerfCommandName {

  public IJPerfCommandNameImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull IJPerfVisitor visitor) {
    visitor.visitCommandName(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof IJPerfVisitor) accept((IJPerfVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  public PsiElement setName(@NotNull String name) {
    return IJPerfPsiImplUtil.setName(this, name);
  }

  @Override
  public String getName() {
    return IJPerfPsiImplUtil.getName(this);
  }

  @Override
  public PsiElement getNameIdentifier() {
    return IJPerfPsiImplUtil.getNameIdentifier(this);
  }

}
