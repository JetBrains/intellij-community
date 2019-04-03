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

public class BashHeredocImpl extends BashCompositeElementImpl implements BashHeredoc {

  public BashHeredocImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull BashVisitor visitor) {
    visitor.visitHeredoc(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof BashVisitor) accept((BashVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public BashCommandsList getCommandsList() {
    return findChildByClass(BashCommandsList.class);
  }

  @Override
  @Nullable
  public PsiElement getHeredocMarkerEnd() {
    return findChildByType(HEREDOC_MARKER_END);
  }

  @Override
  @Nullable
  public PsiElement getHeredocMarkerIgnoringTabsEnd() {
    return findChildByType(HEREDOC_MARKER_IGNORING_TABS_END);
  }

  @Override
  @NotNull
  public PsiElement getHeredocMarkerStart() {
    return findNotNullChildByType(HEREDOC_MARKER_START);
  }

  @Override
  @NotNull
  public PsiElement getHeredocMarkerTag() {
    return findNotNullChildByType(HEREDOC_MARKER_TAG);
  }

  @Override
  @Nullable
  public PsiElement getPipe() {
    return findChildByType(PIPE);
  }

}
