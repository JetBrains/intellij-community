// This is a generated file. Not intended for manual editing.
package com.intellij.mermaid.lang.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.intellij.mermaid.lang.parser.MermaidElements.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.mermaid.lang.psi.*;

public class MermaidCommitStatementImpl extends ASTWrapperPsiElement implements MermaidCommitStatement {

  public MermaidCommitStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull MermaidVisitor visitor) {
    visitor.visitCommitStatement(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof MermaidVisitor) accept((MermaidVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public MermaidCommitIdAttribute getCommitIdAttribute() {
    return findChildByClass(MermaidCommitIdAttribute.class);
  }

  @Override
  @Nullable
  public MermaidCommitMsgAttribute getCommitMsgAttribute() {
    return findChildByClass(MermaidCommitMsgAttribute.class);
  }

  @Override
  @Nullable
  public MermaidCommitTagAttribute getCommitTagAttribute() {
    return findChildByClass(MermaidCommitTagAttribute.class);
  }

  @Override
  @Nullable
  public MermaidCommitTypeAttribute getCommitTypeAttribute() {
    return findChildByClass(MermaidCommitTypeAttribute.class);
  }

  @Override
  @Nullable
  public MermaidCommitArg getCommitArg() {
    return findChildByClass(MermaidCommitArg.class);
  }

}
