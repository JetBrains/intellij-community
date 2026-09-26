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

public class MermaidGitGraphBodyImpl extends ASTWrapperPsiElement implements MermaidGitGraphBody {

  public MermaidGitGraphBodyImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull MermaidVisitor visitor) {
    visitor.visitGitGraphBody(this);
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
  public List<MermaidBranchStatement> getBranchStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidBranchStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidCheckoutStatement> getCheckoutStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidCheckoutStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidCherryPickStatement> getCherryPickStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidCherryPickStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidCommitStatement> getCommitStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidCommitStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidMergeStatement> getMergeStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidMergeStatement.class);
  }

}
