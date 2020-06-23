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

public class ShCommandImpl extends ShCompositeElementImpl implements ShCommand {

  public ShCommandImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull ShVisitor visitor) {
    visitor.visitCommand(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof ShVisitor) accept((ShVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public ShCommand getCommand() {
    return findChildByClass(ShCommand.class);
  }

  @Override
  @NotNull
  public List<ShRedirection> getRedirectionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, ShRedirection.class);
  }

}
