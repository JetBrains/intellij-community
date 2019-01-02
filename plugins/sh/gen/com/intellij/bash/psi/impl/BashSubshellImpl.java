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

public class BashSubshellImpl extends BashCompositeElementImpl implements BashSubshell {

  public BashSubshellImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull BashVisitor visitor) {
    visitor.visitSubshell(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof BashVisitor) accept((BashVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public BashCompoundList getCompoundList() {
    return findChildByClass(BashCompoundList.class);
  }

  @Override
  @NotNull
  public PsiElement getLeftParen() {
    return findNotNullChildByType(LEFT_PAREN);
  }

  @Override
  @Nullable
  public PsiElement getRightParen() {
    return findChildByType(RIGHT_PAREN);
  }

}
