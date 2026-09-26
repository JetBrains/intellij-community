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

public class ShProcessSubstitutionImpl extends ShCompositeElementImpl implements ShProcessSubstitution {

  public ShProcessSubstitutionImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull ShVisitor visitor) {
    visitor.visitProcessSubstitution(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof ShVisitor) accept((ShVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public ShCompoundList getCompoundList() {
    return findChildByClass(ShCompoundList.class);
  }

  @Override
  @Nullable
  public PsiElement getInputProcessSubstitution() {
    return findChildByType(INPUT_PROCESS_SUBSTITUTION);
  }

  @Override
  @Nullable
  public PsiElement getOutputProcessSubstitution() {
    return findChildByType(OUTPUT_PROCESS_SUBSTITUTION);
  }

  @Override
  @Nullable
  public PsiElement getRightParen() {
    return findChildByType(RIGHT_PAREN);
  }

}
