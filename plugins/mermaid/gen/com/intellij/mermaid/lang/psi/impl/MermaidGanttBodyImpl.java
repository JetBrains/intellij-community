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

public class MermaidGanttBodyImpl extends ASTWrapperPsiElement implements MermaidGanttBody {

  public MermaidGanttBodyImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull MermaidVisitor visitor) {
    visitor.visitGanttBody(this);
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
  public List<MermaidDirective> getDirectiveList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidDirective.class);
  }

  @Override
  @NotNull
  public List<MermaidGanttAxisFormatStatement> getGanttAxisFormatStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidGanttAxisFormatStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidGanttClickStatement> getGanttClickStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidGanttClickStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidGanttDataStatement> getGanttDataStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidGanttDataStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidGanttDateFormatStatement> getGanttDateFormatStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidGanttDateFormatStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidGanttExcludesStatement> getGanttExcludesStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidGanttExcludesStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidGanttIncludesStatement> getGanttIncludesStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidGanttIncludesStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidGanttInclusiveEndDatesStatement> getGanttInclusiveEndDatesStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidGanttInclusiveEndDatesStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidGanttSectionStatement> getGanttSectionStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidGanttSectionStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidGanttTickIntervalStatement> getGanttTickIntervalStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidGanttTickIntervalStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidGanttTodayMarkerStatement> getGanttTodayMarkerStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidGanttTodayMarkerStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidGanttTopAxisStatement> getGanttTopAxisStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidGanttTopAxisStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidGanttWeekdayStatement> getGanttWeekdayStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidGanttWeekdayStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidTitleStatement> getTitleStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidTitleStatement.class);
  }

}
