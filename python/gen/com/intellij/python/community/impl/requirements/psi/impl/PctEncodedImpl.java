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

public class PctEncodedImpl extends ASTWrapperPsiElement implements PctEncoded {

  public PctEncodedImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull Visitor visitor) {
    visitor.visitPctEncoded(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof Visitor) accept((Visitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public Hexdig getHexdig() {
    return findNotNullChildByClass(Hexdig.class);
  }

}
