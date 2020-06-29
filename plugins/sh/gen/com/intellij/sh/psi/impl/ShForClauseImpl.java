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

public class ShForClauseImpl extends ShCompositeElementImpl implements ShForClause {

  public ShForClauseImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull ShVisitor visitor) {
    visitor.visitForClause(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof ShVisitor) accept((ShVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<ShArithmeticExpansion> getArithmeticExpansionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, ShArithmeticExpansion.class);
  }

  @Override
  @NotNull
  public List<ShBraceExpansion> getBraceExpansionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, ShBraceExpansion.class);
  }

  @Override
  @NotNull
  public List<ShCommand> getCommandList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, ShCommand.class);
  }

  @Override
  @NotNull
  public List<ShExpression> getExpressionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, ShExpression.class);
  }

  @Override
  @Nullable
  public ShListTerminator getListTerminator() {
    return findChildByClass(ShListTerminator.class);
  }

  @Override
  @NotNull
  public List<ShNumber> getNumberList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, ShNumber.class);
  }

  @Override
  @NotNull
  public List<ShShellParameterExpansion> getShellParameterExpansionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, ShShellParameterExpansion.class);
  }

  @Override
  @NotNull
  public List<ShString> getStringList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, ShString.class);
  }

  @Override
  @NotNull
  public List<ShVariable> getVariableList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, ShVariable.class);
  }

  @Override
  @Nullable
  public PsiElement getLeftDoubleParen() {
    return findChildByType(LEFT_DOUBLE_PAREN);
  }

  @Override
  @Nullable
  public PsiElement getRightDoubleParen() {
    return findChildByType(RIGHT_DOUBLE_PAREN);
  }

  @Override
  @Nullable
  public PsiElement getIn() {
    return findChildByType(IN);
  }

}
