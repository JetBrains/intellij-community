// This is a generated file. Not intended for manual editing.
package com.intellij.python.community.impl.requirements.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.intellij.python.community.impl.requirements.psi.RequirementsTypes.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.python.community.impl.requirements.psi.*;

public class Ls32Impl extends ASTWrapperPsiElement implements Ls32 {

  public Ls32Impl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull Visitor visitor) {
    visitor.visitLs32(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof Visitor) accept((Visitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public IPv4Address getIPv4Address() {
    return findChildByClass(IPv4Address.class);
  }

  @Override
  @Nullable
  public H16 getH16() {
    return findChildByClass(H16.class);
  }

  @Override
  @Nullable
  public H16Colon getH16Colon() {
    return findChildByClass(H16Colon.class);
  }

}
