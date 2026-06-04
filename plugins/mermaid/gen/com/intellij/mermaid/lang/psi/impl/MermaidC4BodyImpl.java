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

public class MermaidC4BodyImpl extends ASTWrapperPsiElement implements MermaidC4Body {

  public MermaidC4BodyImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull MermaidVisitor visitor) {
    visitor.visitC4Body(this);
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
  public List<MermaidBoundaryStatement> getBoundaryStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidBoundaryStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidC4ComponentStatement> getC4ComponentStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidC4ComponentStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidDirectionStatement> getDirectionStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidDirectionStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidTitleStatement> getTitleStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidTitleStatement.class);
  }

}
