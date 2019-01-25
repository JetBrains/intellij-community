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

public class BashLetCommandImpl extends BashCommandImpl implements BashLetCommand {

  public BashLetCommandImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull BashVisitor visitor) {
    visitor.visitLetCommand(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof BashVisitor) accept((BashVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public BashExpression getExpression() {
    return findChildByClass(BashExpression.class);
  }

  @Override
  @NotNull
  public PsiElement getLet() {
    return findNotNullChildByType(LET);
  }

}
