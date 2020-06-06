// This is a generated file. Not intended for manual editing.
package com.intellij.sh.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.intellij.sh.ShTypes.*;
import com.intellij.sh.psi.*;

public class ShRedirectionImpl extends ShCompositeElementImpl implements ShRedirection {

  public ShRedirectionImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull ShVisitor visitor) {
    visitor.visitRedirection(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof ShVisitor) accept((ShVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public ShArithmeticExpansion getArithmeticExpansion() {
    return findChildByClass(ShArithmeticExpansion.class);
  }

  @Override
  @Nullable
  public ShBraceExpansion getBraceExpansion() {
    return findChildByClass(ShBraceExpansion.class);
  }

  @Override
  @NotNull
  public List<ShCommand> getCommandList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, ShCommand.class);
  }

  @Override
  @Nullable
  public ShNumber getNumber() {
    return findChildByClass(ShNumber.class);
  }

  @Override
  @Nullable
  public ShProcessSubstitution getProcessSubstitution() {
    return findChildByClass(ShProcessSubstitution.class);
  }

  @Override
  @Nullable
  public ShShellParameterExpansion getShellParameterExpansion() {
    return findChildByClass(ShShellParameterExpansion.class);
  }

  @Override
  @Nullable
  public ShString getString() {
    return findChildByClass(ShString.class);
  }

  @Override
  @Nullable
  public ShVariable getVariable() {
    return findChildByClass(ShVariable.class);
  }

}
