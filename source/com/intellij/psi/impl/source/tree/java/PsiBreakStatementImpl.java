package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.PsiLabelReference;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class PsiBreakStatementImpl extends CompositePsiElement implements PsiBreakStatement, Constants {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiBreakStatementImpl");

  public PsiBreakStatementImpl() {
    super(BREAK_STATEMENT);
  }

  public PsiIdentifier getLabelIdentifier() {
    return (PsiIdentifier)findChildByRoleAsPsiElement(ChildRole.LABEL);
  }

  public PsiStatement findExitedStatement() {
    PsiIdentifier label = getLabelIdentifier();
    if (label == null){
      for(ASTNode parent = getTreeParent(); parent != null; parent = parent.getTreeParent()){
        IElementType i = parent.getElementType();
        if (i == FOR_STATEMENT || i == WHILE_STATEMENT || i == DO_WHILE_STATEMENT || i == SWITCH_STATEMENT || i == FOREACH_STATEMENT) {
          return (PsiStatement)SourceTreeToPsiMap.treeElementToPsi(parent);
        }
        else if (i == METHOD || i == CLASS_INITIALIZER) {
          return null; // do not pass through anonymous/local class
        }
      }
    }
    else{
      String labelName = label.getText();
      for(CompositeElement parent = getTreeParent(); parent != null; parent = parent.getTreeParent()){
        if (parent.getElementType() == LABELED_STATEMENT){
          ASTNode statementLabel = parent.findChildByRole(ChildRole.LABEL_NAME);
          if (statementLabel.getText().equals(labelName)){
            return ((PsiLabeledStatement)SourceTreeToPsiMap.treeElementToPsi(parent)).getStatement();
          }
        }

        if (parent.getElementType() == METHOD || parent.getElementType() == CLASS_INITIALIZER) return null; // do not pass through anonymous/local class
      }
    }
    return null;
  }

  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.BREAK_KEYWORD:
        return TreeUtil.findChild(this, BREAK_KEYWORD);

      case ChildRole.LABEL:
        return TreeUtil.findChild(this, IDENTIFIER);

      case ChildRole.CLOSING_SEMICOLON:
        return TreeUtil.findChildBackward(this, SEMICOLON);
    }
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == BREAK_KEYWORD) {
      return ChildRole.BREAK_KEYWORD;
    }
    else if (i == IDENTIFIER) {
      return ChildRole.LABEL;
    }
    else if (i == SEMICOLON) {
      return ChildRole.CLOSING_SEMICOLON;
    }
    else {
      return ChildRole.NONE;
    }
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitBreakStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public PsiReference getReference() {
    final PsiReference[] references = getReferences();
    if (references != null && references.length > 0)
      return references[0];
    return null;
  }

  @NotNull
  public PsiReference[] getReferences() {
    if (getLabelIdentifier() == null)
      return PsiReference.EMPTY_ARRAY;
    return new PsiReference[]{new PsiLabelReference(this, getLabelIdentifier())};
  }

  public String toString() {
    return "PsiBreakStatement";
  }
}
