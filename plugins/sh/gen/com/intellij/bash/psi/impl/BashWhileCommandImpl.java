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

public class BashWhileCommandImpl extends BashCommandImpl implements BashWhileCommand {

  public BashWhileCommandImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull BashVisitor visitor) {
    visitor.visitWhileCommand(this);
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
  @Nullable
  public BashDoBlock getDoBlock() {
    return findChildByClass(BashDoBlock.class);
  }

  @Override
  @NotNull
  public PsiElement getWhile() {
    return findNotNullChildByType(WHILE);
  }

}
