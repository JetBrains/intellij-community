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

public class MermaidRequirementDiagramBodyImpl extends ASTWrapperPsiElement implements MermaidRequirementDiagramBody {

  public MermaidRequirementDiagramBodyImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull MermaidVisitor visitor) {
    visitor.visitRequirementDiagramBody(this);
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
  public List<MermaidElementDef> getElementDefList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidElementDef.class);
  }

  @Override
  @NotNull
  public List<MermaidRelationshipDef> getRelationshipDefList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidRelationshipDef.class);
  }

  @Override
  @NotNull
  public List<MermaidRequirementDef> getRequirementDefList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidRequirementDef.class);
  }

}
