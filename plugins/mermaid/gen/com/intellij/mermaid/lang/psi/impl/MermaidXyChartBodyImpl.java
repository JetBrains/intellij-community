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

public class MermaidXyChartBodyImpl extends ASTWrapperPsiElement implements MermaidXyChartBody {

  public MermaidXyChartBodyImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull MermaidVisitor visitor) {
    visitor.visitXyChartBody(this);
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
  public List<MermaidBarStatement> getBarStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidBarStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidDirective> getDirectiveList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidDirective.class);
  }

  @Override
  @NotNull
  public List<MermaidLineStatement> getLineStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidLineStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidTitleStatement> getTitleStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidTitleStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidXAxisStatement> getXAxisStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidXAxisStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidYAxisStatement> getYAxisStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidYAxisStatement.class);
  }

}
