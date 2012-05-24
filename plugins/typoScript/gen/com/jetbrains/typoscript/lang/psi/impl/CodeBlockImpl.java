// This is a generated file. Not intended for manual editing.
package com.jetbrains.typoscript.lang.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.jetbrains.typoscript.lang.TypoScriptElementTypes.*;
import com.jetbrains.typoscript.lang.psi.TypoScriptCompositeElementImpl;
import com.jetbrains.typoscript.lang.psi.*;

public class CodeBlockImpl extends TypoScriptCompositeElementImpl implements CodeBlock {

  public CodeBlockImpl(ASTNode node) {
    super(node);
  }

  @Override
  @NotNull
  public List<Assignment> getAssignmentList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, Assignment.class);
  }

  @Override
  @NotNull
  public List<CodeBlock> getCodeBlockList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CodeBlock.class);
  }

  @Override
  @NotNull
  public List<ConditionElement> getConditionElementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, ConditionElement.class);
  }

  @Override
  @NotNull
  public List<Copying> getCopyingList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, Copying.class);
  }

  @Override
  @NotNull
  public List<IncludeStatementElement> getIncludeStatementElementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, IncludeStatementElement.class);
  }

  @Override
  @NotNull
  public List<MultilineValueAssignment> getMultilineValueAssignmentList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, MultilineValueAssignment.class);
  }

  @Override
  @NotNull
  public ObjectPath getObjectPath() {
    return findNotNullChildByClass(ObjectPath.class);
  }

  @Override
  @NotNull
  public List<Unsetting> getUnsettingList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, Unsetting.class);
  }

  @Override
  @NotNull
  public List<ValueModification> getValueModificationList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, ValueModification.class);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof Visitor) ((Visitor)visitor).visitCodeBlock(this);
    else super.accept(visitor);
  }

}
