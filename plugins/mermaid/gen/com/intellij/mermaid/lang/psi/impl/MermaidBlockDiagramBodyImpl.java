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

public class MermaidBlockDiagramBodyImpl extends ASTWrapperPsiElement implements MermaidBlockDiagramBody {

  public MermaidBlockDiagramBodyImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull MermaidVisitor visitor) {
    visitor.visitBlockDiagramBody(this);
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
  public List<MermaidBlockDiagramComplexStatement> getBlockDiagramComplexStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidBlockDiagramComplexStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidBlockDiagramNodeStatement> getBlockDiagramNodeStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidBlockDiagramNodeStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidBlockStatement> getBlockStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidBlockStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidClassDefStatement> getClassDefStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidClassDefStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidColumnsStatement> getColumnsStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidColumnsStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidDirective> getDirectiveList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidDirective.class);
  }

  @Override
  @NotNull
  public List<MermaidFlowchartClassStatement> getFlowchartClassStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidFlowchartClassStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidSpaceStatement> getSpaceStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidSpaceStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidStyleStatement> getStyleStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidStyleStatement.class);
  }

}
