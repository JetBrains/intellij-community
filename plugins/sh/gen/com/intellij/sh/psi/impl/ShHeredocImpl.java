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

public class ShHeredocImpl extends ShCompositeElementImpl implements ShHeredoc {

  public ShHeredocImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull ShVisitor visitor) {
    visitor.visitHeredoc(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof ShVisitor) accept((ShVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public ShCommandsList getCommandsList() {
    return findChildByClass(ShCommandsList.class);
  }

  @Override
  @Nullable
  public PsiElement getAmp() {
    return findChildByType(AMP);
  }

  @Override
  @Nullable
  public PsiElement getAndAnd() {
    return findChildByType(AND_AND);
  }

  @Override
  @Nullable
  public PsiElement getHeredocContent() {
    return findChildByType(HEREDOC_CONTENT);
  }

  @Override
  @Nullable
  public PsiElement getHeredocMarkerEnd() {
    return findChildByType(HEREDOC_MARKER_END);
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
  public PsiElement getOrOr() {
    return findChildByType(OR_OR);
  }

  @Override
  @Nullable
  public PsiElement getPipe() {
    return findChildByType(PIPE);
  }

  @Override
  @Nullable
  public PsiElement getPipeAmp() {
    return findChildByType(PIPE_AMP);
  }

  @Override
  @Nullable
  public PsiElement getSemi() {
    return findChildByType(SEMI);
  }

}
