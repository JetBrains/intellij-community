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

public class MermaidClassBodyImpl extends ASTWrapperPsiElement implements MermaidClassBody {

  public MermaidClassBodyImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull MermaidVisitor visitor) {
    visitor.visitClassBody(this);
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
  public List<MermaidAnnotationStatement> getAnnotationStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidAnnotationStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidClassDiagramClickStatement> getClassDiagramClickStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidClassDiagramClickStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidClassDiagramNoteStatement> getClassDiagramNoteStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidClassDiagramNoteStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidClassStatement> getClassStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidClassStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidDirectionStatement> getDirectionStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidDirectionStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidMemberStatement> getMemberStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidMemberStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidNamespaceStatement> getNamespaceStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidNamespaceStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidRelationStatement> getRelationStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidRelationStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidStyleStatement> getStyleStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidStyleStatement.class);
  }

}
