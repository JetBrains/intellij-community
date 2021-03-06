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

public class ShCommandSubstitutionCommandImpl extends ShCommandImpl implements ShCommandSubstitutionCommand {

  public ShCommandSubstitutionCommandImpl(ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull ShVisitor visitor) {
    visitor.visitCommandSubstitutionCommand(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof ShVisitor) accept((ShVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public PsiElement getCloseBackquote() {
    return findChildByType(CLOSE_BACKQUOTE);
  }

  @Override
  @NotNull
  public PsiElement getOpenBackquote() {
    return findNotNullChildByType(OPEN_BACKQUOTE);
  }

}
