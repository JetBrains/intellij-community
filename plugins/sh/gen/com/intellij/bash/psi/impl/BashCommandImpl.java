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

public class BashCommandImpl extends BashCompositeElementImpl implements BashCommand {

  public BashCommandImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull BashVisitor visitor) {
    visitor.visitCommand(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof BashVisitor) accept((BashVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public BashRedirectionList getRedirectionList() {
    return findChildByClass(BashRedirectionList.class);
  }

  @Override
  @Nullable
  public BashShellCommand getShellCommand() {
    return findChildByClass(BashShellCommand.class);
  }

  @Override
  @Nullable
  public BashSimpleCommand getSimpleCommand() {
    return findChildByClass(BashSimpleCommand.class);
  }

}
