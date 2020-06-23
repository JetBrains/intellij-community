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

public class ShSimpleCommandElementImpl extends ShCompositeElementImpl implements ShSimpleCommandElement {

  public ShSimpleCommandElementImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull ShVisitor visitor) {
    visitor.visitSimpleCommandElement(this);
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
  @Nullable
  public ShCommand getCommand() {
    return findChildByClass(ShCommand.class);
  }

  @Override
  @Nullable
  public ShLiteral getLiteral() {
    return findChildByClass(ShLiteral.class);
  }

  @Override
  @Nullable
  public ShOldArithmeticExpansion getOldArithmeticExpansion() {
    return findChildByClass(ShOldArithmeticExpansion.class);
  }

  @Override
  @Nullable
  public ShRedirection getRedirection() {
    return findChildByClass(ShRedirection.class);
  }

  @Override
  @Nullable
  public ShShellParameterExpansion getShellParameterExpansion() {
    return findChildByClass(ShShellParameterExpansion.class);
  }

  @Override
  @Nullable
  public ShVariable getVariable() {
    return findChildByClass(ShVariable.class);
  }

  @Override
  @Nullable
  public PsiElement getBang() {
    return findChildByType(BANG);
  }

  @Override
  @Nullable
  public PsiElement getDollar() {
    return findChildByType(DOLLAR);
  }

  @Override
  @Nullable
  public PsiElement getFiledescriptor() {
    return findChildByType(FILEDESCRIPTOR);
  }

}
