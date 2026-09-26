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

public class MermaidBlockDiagramNodeStatementInnerImpl extends ASTWrapperPsiElement implements MermaidBlockDiagramNodeStatementInner {

  public MermaidBlockDiagramNodeStatementInnerImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull MermaidVisitor visitor) {
    visitor.visitBlockDiagramNodeStatementInner(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof MermaidVisitor) accept((MermaidVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<MermaidBlockDiagramArrow> getBlockDiagramArrowList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidBlockDiagramArrow.class);
  }

  @Override
  @NotNull
  public List<MermaidBlockDiagramNode> getBlockDiagramNodeList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidBlockDiagramNode.class);
  }

  @Override
  @Nullable
  public MermaidBlockSize getBlockSize() {
    return findChildByClass(MermaidBlockSize.class);
  }

}
