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

public class MermaidSankeyRecordStatementImpl extends ASTWrapperPsiElement implements MermaidSankeyRecordStatement {

  public MermaidSankeyRecordStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull MermaidVisitor visitor) {
    visitor.visitSankeyRecordStatement(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof MermaidVisitor) accept((MermaidVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public MermaidComplexSankeyText getComplexSankeyText() {
    return findChildByClass(MermaidComplexSankeyText.class);
  }

  @Override
  @NotNull
  public List<MermaidIdentifyingComplexSankeyText> getIdentifyingComplexSankeyTextList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidIdentifyingComplexSankeyText.class);
  }

  @Override
  @NotNull
  public List<MermaidIdentifyingQuotedSankeyField> getIdentifyingQuotedSankeyFieldList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidIdentifyingQuotedSankeyField.class);
  }

  @Override
  @Nullable
  public MermaidQuotedSankeyField getQuotedSankeyField() {
    return findChildByClass(MermaidQuotedSankeyField.class);
  }

}
