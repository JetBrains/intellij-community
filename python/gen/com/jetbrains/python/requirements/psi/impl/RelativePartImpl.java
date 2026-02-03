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

public class RelativePartImpl extends ASTWrapperPsiElement implements RelativePart {

  public RelativePartImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull Visitor visitor) {
    visitor.visitRelativePart(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof Visitor) accept((Visitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public Authority getAuthority() {
    return findChildByClass(Authority.class);
  }

  @Override
  @Nullable
  public PathAbempty getPathAbempty() {
    return findChildByClass(PathAbempty.class);
  }

  @Override
  @Nullable
  public PathAbsolute getPathAbsolute() {
    return findChildByClass(PathAbsolute.class);
  }

  @Override
  @Nullable
  public PathEmpty getPathEmpty() {
    return findChildByClass(PathEmpty.class);
  }

  @Override
  @Nullable
  public PathNoscheme getPathNoscheme() {
    return findChildByClass(PathNoscheme.class);
  }

}
