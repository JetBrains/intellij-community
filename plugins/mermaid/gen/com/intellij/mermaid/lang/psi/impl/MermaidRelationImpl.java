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

public class MermaidRelationImpl extends ASTWrapperPsiElement implements MermaidRelation {

  public MermaidRelationImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull MermaidVisitor visitor) {
    visitor.visitRelation(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof MermaidVisitor) accept((MermaidVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<MermaidCardinality> getCardinalityList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidCardinality.class);
  }

  @Override
  @NotNull
  public MermaidLineType getLineType() {
    return findNotNullChildByClass(MermaidLineType.class);
  }

  @Override
  @Nullable
  public MermaidRelationTypeLeft getRelationTypeLeft() {
    return findChildByClass(MermaidRelationTypeLeft.class);
  }

  @Override
  @Nullable
  public MermaidRelationTypeRight getRelationTypeRight() {
    return findChildByClass(MermaidRelationTypeRight.class);
  }

}
