// This is a generated file. Not intended for manual editing.
package com.intellij.python.community.impl.requirements.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.intellij.python.community.impl.requirements.psi.RequirementsTypes.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.python.community.impl.requirements.psi.*;

public class MarkerExprImpl extends ASTWrapperPsiElement implements MarkerExpr {

  public MarkerExprImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull Visitor visitor) {
    visitor.visitMarkerExpr(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof Visitor) accept((Visitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public MarkerOp getMarkerOp() {
    return findChildByClass(MarkerOp.class);
  }

  @Override
  @Nullable
  public MarkerOr getMarkerOr() {
    return findChildByClass(MarkerOr.class);
  }

  @Override
  @NotNull
  public List<MarkerVar> getMarkerVarList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MarkerVar.class);
  }

}
