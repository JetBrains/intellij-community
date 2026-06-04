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

public class MermaidErBodyImpl extends ASTWrapperPsiElement implements MermaidErBody {

  public MermaidErBodyImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull MermaidVisitor visitor) {
    visitor.visitErBody(this);
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
  public List<MermaidEntityDeclaration> getEntityDeclarationList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidEntityDeclaration.class);
  }

  @Override
  @NotNull
  public List<MermaidErIdentifier> getErIdentifierList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidErIdentifier.class);
  }

  @Override
  @NotNull
  public List<MermaidErIdentifierAlias> getErIdentifierAliasList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidErIdentifierAlias.class);
  }

  @Override
  @NotNull
  public List<MermaidErRelationStatement> getErRelationStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MermaidErRelationStatement.class);
  }

}
