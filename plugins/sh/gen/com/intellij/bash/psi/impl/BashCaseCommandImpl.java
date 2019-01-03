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

public class BashCaseCommandImpl extends BashCommandImpl implements BashCaseCommand {

  public BashCaseCommandImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull BashVisitor visitor) {
    visitor.visitCaseCommand(this);
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
  @NotNull
  public List<BashCaseClause> getCaseClauseList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, BashCaseClause.class);
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
  public PsiElement getCase() {
    return findNotNullChildByType(CASE);
  }

  @Override
  @Nullable
  public PsiElement getEsac() {
    return findChildByType(ESAC);
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

  @Override
  @Nullable
  public PsiElement getWord() {
    return findChildByType(WORD);
  }

}
