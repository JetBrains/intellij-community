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

public class IPv6AddressImpl extends ASTWrapperPsiElement implements IPv6Address {

  public IPv6AddressImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull Visitor visitor) {
    visitor.visitIPv6Address(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof Visitor) accept((Visitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public H16 getH16() {
    return findChildByClass(H16.class);
  }

  @Override
  @NotNull
  public List<H16Colon> getH16ColonList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, H16Colon.class);
  }

  @Override
  @Nullable
  public Ls32 getLs32() {
    return findChildByClass(Ls32.class);
  }

}
