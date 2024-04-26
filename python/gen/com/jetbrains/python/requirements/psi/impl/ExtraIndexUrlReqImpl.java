// This is a generated file. Not intended for manual editing.
package com.jetbrains.python.requirements.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.jetbrains.python.requirements.psi.RequirementsTypes.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.jetbrains.python.requirements.psi.*;

public class ExtraIndexUrlReqImpl extends ASTWrapperPsiElement implements ExtraIndexUrlReq {

  public ExtraIndexUrlReqImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull Visitor visitor) {
    visitor.visitExtraIndexUrlReq(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof Visitor) accept((Visitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public UriReference getUriReference() {
    return findChildByClass(UriReference.class);
  }

}
