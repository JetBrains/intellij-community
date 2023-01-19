// This is a generated file. Not intended for manual editing.
package com.intellij.sh.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.intellij.sh.ShTypes.*;
import com.intellij.sh.psi.*;

public class ShAssignmentCommandImpl extends ShAssignmentCommandMixin implements ShAssignmentCommand {

  public ShAssignmentCommandImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull ShVisitor visitor) {
    visitor.visitAssignmentCommand(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof ShVisitor) accept((ShVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public ShArrayExpression getArrayExpression() {
    return findChildByClass(ShArrayExpression.class);
  }

  @Override
  @Nullable
  public ShAssignmentList getAssignmentList() {
    return findChildByClass(ShAssignmentList.class);
  }

  @Override
  @NotNull
  public ShLiteral getLiteral() {
    return findNotNullChildByClass(ShLiteral.class);
  }

  @Override
  @Nullable
  public PsiElement getPlusAssign() {
    return findChildByType(PLUS_ASSIGN);
  }

}
