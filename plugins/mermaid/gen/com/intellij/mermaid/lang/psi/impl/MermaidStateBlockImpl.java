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

public class MermaidStateBlockImpl extends ASTWrapperPsiElement implements MermaidStateBlock {

  public MermaidStateBlockImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull MermaidVisitor visitor) {
    visitor.visitStateBlock(this);
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
  public List<MermaidCompositeStateDeclaration> getCompositeStateDeclarationList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidCompositeStateDeclaration.class);
  }

  @Override
  @NotNull
  public List<MermaidCssClassStatement> getCssClassStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidCssClassStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidDirectionStatement> getDirectionStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidDirectionStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidDirective> getDirectiveList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidDirective.class);
  }

  @Override
  @NotNull
  public List<MermaidDividerStatement> getDividerStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidDividerStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidStateClassDefStatement> getStateClassDefStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidStateClassDefStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidStateDeclaration> getStateDeclarationList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidStateDeclaration.class);
  }

  @Override
  @NotNull
  public List<MermaidStateId> getStateIdList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidStateId.class);
  }

  @Override
  @NotNull
  public List<MermaidStateNote> getStateNoteList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidStateNote.class);
  }

  @Override
  @NotNull
  public List<MermaidStateRelationStatement> getStateRelationStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidStateRelationStatement.class);
  }

}
