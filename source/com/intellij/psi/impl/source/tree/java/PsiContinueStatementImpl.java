package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiLabelReference;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class PsiContinueStatementImpl extends CompositePsiElement implements PsiContinueStatement, Constants {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiContinueStatementImpl");

  public PsiContinueStatementImpl() {
    super(CONTINUE_STATEMENT);
  }

  public PsiIdentifier getLabelIdentifier() {
    return (PsiIdentifier)findChildByRoleAsPsiElement(ChildRole.LABEL);
  }

  public PsiStatement findContinuedStatement() {
    PsiIdentifier label = getLabelIdentifier();
    if (label == null){
      for(ASTNode parent = getTreeParent(); parent != null; parent = parent.getTreeParent()){
        IElementType i = parent.getElementType();
        if (i == FOR_STATEMENT || i == FOREACH_STATEMENT || i == WHILE_STATEMENT || i == DO_WHILE_STATEMENT) {
          return (PsiStatement)SourceTreeToPsiMap.treeElementToPsi(parent);
        }
        if (i == METHOD || i == CLASS_INITIALIZER) {
          return null;
        }
      }
    }
    else{
      String labelName = label.getText();
      for(CompositeElement parent = getTreeParent(); parent != null; parent = parent.getTreeParent()){
        if (parent.getElementType() == LABELED_STATEMENT){
          TreeElement statementLabel = (TreeElement)parent.findChildByRole(ChildRole.LABEL_NAME);
          if (statementLabel.textMatches(labelName)){
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

      case ChildRole.CONTINUE_KEYWORD:
        return TreeUtil.findChild(this, CONTINUE_KEYWORD);

      case ChildRole.LABEL:
        return TreeUtil.findChild(this, IDENTIFIER);

      case ChildRole.CLOSING_SEMICOLON:
        return TreeUtil.findChildBackward(this, SEMICOLON);
    }
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == CONTINUE_KEYWORD) {
      return ChildRole.CONTINUE_KEYWORD;
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
      ((JavaElementVisitor)visitor).visitContinueStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiContinueStatement";
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
}
