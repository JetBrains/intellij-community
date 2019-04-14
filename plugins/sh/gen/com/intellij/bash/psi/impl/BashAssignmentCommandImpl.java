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

public class BashAssignmentCommandImpl extends BashCommandImpl implements BashAssignmentCommand {

  public BashAssignmentCommandImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull BashVisitor visitor) {
    visitor.visitAssignmentCommand(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof BashVisitor) accept((BashVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public BashArrayExpression getArrayExpression() {
    return findChildByClass(BashArrayExpression.class);
  }

  @Override
  @Nullable
  public BashAssignmentList getAssignmentList() {
    return findChildByClass(BashAssignmentList.class);
  }

  @Override
  @NotNull
  public BashLiteral getLiteral() {
    return findNotNullChildByClass(BashLiteral.class);
  }

  @Override
  @Nullable
  public PsiElement getPlusAssign() {
    return findChildByType(PLUS_ASSIGN);
  }

}
