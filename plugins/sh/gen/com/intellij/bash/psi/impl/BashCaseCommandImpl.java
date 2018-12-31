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

public class BashCaseCommandImpl extends BashCompositeElementImpl implements BashCaseCommand {

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
  public BashCaseClause getCaseClause() {
    return findChildByClass(BashCaseClause.class);
  }

  @Override
  @Nullable
  public BashCaseClauseSequence getCaseClauseSequence() {
    return findChildByClass(BashCaseClauseSequence.class);
  }

  @Override
  @NotNull
  public PsiElement getCase() {
    return findNotNullChildByType(CASE);
  }

  @Override
  @NotNull
  public PsiElement getEsac() {
    return findNotNullChildByType(ESAC);
  }

  @Override
  @NotNull
  public PsiElement getIn() {
    return findNotNullChildByType(IN);
  }

  @Override
  @NotNull
  public PsiElement getWord() {
    return findNotNullChildByType(WORD);
  }

}
