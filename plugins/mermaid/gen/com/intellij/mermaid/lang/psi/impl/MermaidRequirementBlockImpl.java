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

public class MermaidRequirementBlockImpl extends ASTWrapperPsiElement implements MermaidRequirementBlock {

  public MermaidRequirementBlockImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull MermaidVisitor visitor) {
    visitor.visitRequirementBlock(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof MermaidVisitor) accept((MermaidVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<MermaidRequirementIdAttribute> getRequirementIdAttributeList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidRequirementIdAttribute.class);
  }

  @Override
  @NotNull
  public List<MermaidRequirementRiskAttribute> getRequirementRiskAttributeList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidRequirementRiskAttribute.class);
  }

  @Override
  @NotNull
  public List<MermaidRequirementTextAttribute> getRequirementTextAttributeList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidRequirementTextAttribute.class);
  }

  @Override
  @NotNull
  public List<MermaidRequirementVerifyMethodAttribute> getRequirementVerifyMethodAttributeList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidRequirementVerifyMethodAttribute.class);
  }

}
