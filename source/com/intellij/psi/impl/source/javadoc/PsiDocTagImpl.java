package com.intellij.psi.impl.source.javadoc;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.SharedPsiElementImplUtil;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.IncorrectOperationException;

public class PsiDocTagImpl extends CompositePsiElement implements PsiDocTag {
  private static final TokenSet VALUE_BIT_SET = TokenSet.create(new IElementType[]{
    JAVA_CODE_REFERENCE,
    DOC_TAG_VALUE_TOKEN,
    DOC_METHOD_OR_FIELD_REF,
    DOC_PARAMETER_REF,
    DOC_COMMENT_DATA,
    DOC_INLINE_TAG,
    DOC_REFERENCE_HOLDER
  });

  public PsiDocTagImpl() {
    super(DOC_TAG);
  }

  public PsiDocComment getContainingComment() {
    return (PsiDocComment)SourceTreeToPsiMap.treeElementToPsi(getTreeParent());
  }

  public PsiElement getNameElement() {
    return findChildByRoleAsPsiElement(ChildRole.DOC_TAG_NAME);
  }

  public PsiDocTagValue getValueElement() {
    return (PsiDocTagValue)findChildByRoleAsPsiElement(ChildRole.DOC_TAG_VALUE);
  }

  public PsiElement[] getDataElements() {
    return getChildrenAsPsiElements(VALUE_BIT_SET, PSI_ELEMENT_ARRAY_CONSTRUCTOR);
  }

  public String getName() {
    if (getNameElement() == null) return "";
    return getNameElement().getText().substring(1);
  }

  public PsiElement setName(String name) throws IncorrectOperationException {
    SharedPsiElementImplUtil.setName(getNameElement(), name);
    return this;
  }

  public int getChildRole(ASTNode child) {
    assert (child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == DOC_TAG_NAME) {
      return ChildRole.DOC_TAG_NAME;
    }
    else if (i == JavaDocElementType.DOC_COMMENT || i == DOC_INLINE_TAG) {
      return ChildRole.DOC_CONTENT;
    }
    else if (i == DOC_COMMENT_LEADING_ASTERISKS) {
      return ChildRole.DOC_COMMENT_ASTERISKS;
    }
    else if (i == DOC_TAG_VALUE_TOKEN) {
      return ChildRole.DOC_TAG_VALUE;
    }
    else if (i == DOC_METHOD_OR_FIELD_REF || i == DOC_PARAMETER_REF) {
      return ChildRole.DOC_TAG_VALUE;
    }
    else {
      return ChildRole.NONE;
    }
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitDocTag(this);
  }

  public String toString() {
    return "PsiDocTag:" + getNameElement().getText();
  }
}