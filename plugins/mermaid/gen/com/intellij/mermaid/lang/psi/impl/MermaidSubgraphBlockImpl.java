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

public class MermaidSubgraphBlockImpl extends ASTWrapperPsiElement implements MermaidSubgraphBlock {

  public MermaidSubgraphBlockImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull MermaidVisitor visitor) {
    visitor.visitSubgraphBlock(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof MermaidVisitor) accept((MermaidVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<MermaidAccStatement> getAccStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidAccStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidClassDefStatement> getClassDefStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidClassDefStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidDirectionStatement> getDirectionStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidDirectionStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidFlowchartClassStatement> getFlowchartClassStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidFlowchartClassStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidFlowchartClickStatement> getFlowchartClickStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidFlowchartClickStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidLinkStyleStatement> getLinkStyleStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidLinkStyleStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidStyleStatement> getStyleStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidStyleStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidSubgraphStatement> getSubgraphStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidSubgraphStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidVertexStatement> getVertexStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidVertexStatement.class);
  }

}
