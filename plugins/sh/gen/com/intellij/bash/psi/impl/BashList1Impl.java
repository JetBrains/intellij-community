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

public class BashList1Impl extends BashCompositeElementImpl implements BashList1 {

  public BashList1Impl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull BashVisitor visitor) {
    visitor.visitList1(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof BashVisitor) accept((BashVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public BashList1 getList1() {
    return findChildByClass(BashList1.class);
  }

  @Override
  @NotNull
  public BashPipelineCommand getPipelineCommand() {
    return findNotNullChildByClass(BashPipelineCommand.class);
  }

  @Override
  @Nullable
  public PsiElement getAmp() {
    return findChildByType(AMP);
  }

  @Override
  @Nullable
  public PsiElement getAndAnd() {
    return findChildByType(AND_AND);
  }

  @Override
  @Nullable
  public PsiElement getOrOr() {
    return findChildByType(OR_OR);
  }

  @Override
  @Nullable
  public PsiElement getSemi() {
    return findChildByType(SEMI);
  }

}
