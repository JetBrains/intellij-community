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

public class MermaidStateRelationStatementImpl extends ASTWrapperPsiElement implements MermaidStateRelationStatement {

  public MermaidStateRelationStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull MermaidVisitor visitor) {
    visitor.visitStateRelationStatement(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof MermaidVisitor) accept((MermaidVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public MermaidComplexLabel getComplexLabel() {
    return findChildByClass(MermaidComplexLabel.class);
  }

  @Override
  @NotNull
  public List<MermaidStateId> getStateIdList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidStateId.class);
  }

}
