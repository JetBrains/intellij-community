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

public class BashStringImpl extends BashCompositeElementImpl implements BashString {

  public BashStringImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull BashVisitor visitor) {
    visitor.visitString(this);
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
  public List<BashCommand> getCommandList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, BashCommand.class);
  }

  @Override
  @NotNull
  public List<BashShellParameterExpansion> getShellParameterExpansionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, BashShellParameterExpansion.class);
  }

  @Override
  @NotNull
  public List<BashVariable> getVariableList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, BashVariable.class);
  }

  @Override
  @Nullable
  public PsiElement getString2() {
    return findChildByType(STRING2);
  }

  @Override
  @Nullable
  public PsiElement getStringBegin() {
    return findChildByType(STRING_BEGIN);
  }

  @Override
  @Nullable
  public PsiElement getStringEnd() {
    return findChildByType(STRING_END);
  }

}
