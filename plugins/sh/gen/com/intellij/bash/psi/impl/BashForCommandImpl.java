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

public class BashForCommandImpl extends BashCommandImpl implements BashForCommand {

  public BashForCommandImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull BashVisitor visitor) {
    visitor.visitForCommand(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof BashVisitor) accept((BashVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public BashCompoundList getCompoundList() {
    return findNotNullChildByClass(BashCompoundList.class);
  }

  @Override
  @Nullable
  public BashListTerminator getListTerminator() {
    return findChildByClass(BashListTerminator.class);
  }

  @Override
  @Nullable
  public PsiElement getLeftCurly() {
    return findChildByType(LEFT_CURLY);
  }

  @Override
  @Nullable
  public PsiElement getRightCurly() {
    return findChildByType(RIGHT_CURLY);
  }

  @Override
  @Nullable
  public PsiElement getSemi() {
    return findChildByType(SEMI);
  }

  @Override
  @Nullable
  public PsiElement getDo() {
    return findChildByType(DO);
  }

  @Override
  @Nullable
  public PsiElement getDone() {
    return findChildByType(DONE);
  }

  @Override
  @NotNull
  public PsiElement getFor() {
    return findNotNullChildByType(FOR);
  }

  @Override
  @Nullable
  public PsiElement getIn() {
    return findChildByType(IN);
  }

}
