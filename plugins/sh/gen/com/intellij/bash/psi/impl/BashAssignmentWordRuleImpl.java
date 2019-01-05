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

public class BashAssignmentWordRuleImpl extends BashCompositeElementImpl implements BashAssignmentWordRule {

  public BashAssignmentWordRuleImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull BashVisitor visitor) {
    visitor.visitAssignmentWordRule(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof BashVisitor) accept((BashVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public BashArithmeticExpansion getArithmeticExpansion() {
    return findChildByClass(BashArithmeticExpansion.class);
  }

  @Override
  @Nullable
  public BashAssignmentList getAssignmentList() {
    return findChildByClass(BashAssignmentList.class);
  }

  @Override
  @Nullable
  public BashCommand getCommand() {
    return findChildByClass(BashCommand.class);
  }

  @Override
  @Nullable
  public BashShellParameterExpansion getShellParameterExpansion() {
    return findChildByClass(BashShellParameterExpansion.class);
  }

  @Override
  @Nullable
  public BashString getString() {
    return findChildByClass(BashString.class);
  }

  @Override
  @Nullable
  public PsiElement getDollar() {
    return findChildByType(DOLLAR);
  }

  @Override
  @NotNull
  public PsiElement getEq() {
    return findNotNullChildByType(EQ);
  }

  @Override
  @Nullable
  public PsiElement getFiledescriptor() {
    return findChildByType(FILEDESCRIPTOR);
  }

  @Override
  @Nullable
  public PsiElement getAssignmentWord() {
    return findChildByType(ASSIGNMENT_WORD);
  }

  @Override
  @Nullable
  public PsiElement getInt() {
    return findChildByType(INT);
  }

  @Override
  @Nullable
  public PsiElement getNumber() {
    return findChildByType(NUMBER);
  }

  @Override
  @Nullable
  public PsiElement getVariable() {
    return findChildByType(VARIABLE);
  }

}
