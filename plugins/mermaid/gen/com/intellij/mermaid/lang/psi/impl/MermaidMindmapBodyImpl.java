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

public class MermaidMindmapBodyImpl extends ASTWrapperPsiElement implements MermaidMindmapBody {

  public MermaidMindmapBodyImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull MermaidVisitor visitor) {
    visitor.visitMindmapBody(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof MermaidVisitor) accept((MermaidVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<MermaidDirective> getDirectiveList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidDirective.class);
  }

  @Override
  @NotNull
  public List<MermaidIconStatement> getIconStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidIconStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidMindmapClassStatement> getMindmapClassStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidMindmapClassStatement.class);
  }

  @Override
  @NotNull
  public List<MermaidMindmapNodeStatement> getMindmapNodeStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidMindmapNodeStatement.class);
  }

}
