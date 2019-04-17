// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.intellij.bash.BashTypes.*;
import com.intellij.bash.psi.*;

public class BashPatternImpl extends BashCompositeElementImpl implements BashPattern {

  public BashPatternImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull BashVisitor visitor) {
    visitor.visitPattern(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof BashVisitor) accept((BashVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<BashArithmeticExpansion> getArithmeticExpansionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, BashArithmeticExpansion.class);
  }

  @Override
  @NotNull
  public List<BashBashExpansion> getBashExpansionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, BashBashExpansion.class);
  }

  @Override
  @NotNull
  public List<BashCommand> getCommandList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, BashCommand.class);
  }

  @Override
  @NotNull
  public List<BashNumber> getNumberList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, BashNumber.class);
  }

  @Override
  @NotNull
  public List<BashShellParameterExpansion> getShellParameterExpansionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, BashShellParameterExpansion.class);
  }

  @Override
  @NotNull
  public List<BashString> getStringList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, BashString.class);
  }

  @Override
  @NotNull
  public List<BashVariable> getVariableList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, BashVariable.class);
  }

  @Override
  @Nullable
  public PsiElement getLeftParen() {
    return findChildByType(LEFT_PAREN);
  }

}
