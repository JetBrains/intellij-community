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

public class ShLiteralConditionImpl extends ShConditionImpl implements ShLiteralCondition {

  public ShLiteralConditionImpl(ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull ShVisitor visitor) {
    visitor.visitLiteralCondition(this);
  }

  @Override
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

  @Override
  @Nullable
  public PsiElement getWord() {
    return findChildByType(WORD);
  }

}
