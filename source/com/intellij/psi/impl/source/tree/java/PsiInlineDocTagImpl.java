package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiInlineDocTag;
import com.intellij.psi.impl.SharedPsiElementImplUtil;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.IncorrectOperationException;

public class PsiInlineDocTagImpl extends CompositePsiElement implements PsiInlineDocTag {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiInlineDocTagImpl");

  private static final TokenSet VALUE_BIT_SET = TokenSet.create(new IElementType[]{
    JAVA_CODE_REFERENCE,
    DOC_TAG_VALUE_TOKEN,
    DOC_METHOD_OR_FIELD_REF,
    DOC_COMMENT_DATA,
    DOC_INLINE_TAG,
  });

  public PsiInlineDocTagImpl() {
    super(DOC_INLINE_TAG);
  }

  public PsiDocComment getContainingComment() {
    TreeElement scope = getTreeParent();
    while (scope.getElementType() != DOC_COMMENT) {
      scope = scope.getTreeParent();
    }
    return (PsiDocComment)SourceTreeToPsiMap.treeElementToPsi(scope);
  }

  public PsiElement getNameElement() {
    return findChildByRoleAsPsiElement(ChildRole.DOC_TAG_NAME);
  }

  public PsiElement[] getDataElements() {
    return getChildrenAsPsiElements(VALUE_BIT_SET, PSI_ELEMENT_ARRAY_CONSTRUCTOR);
  }

  public PsiDocTagValue getValueElement() {
    return (PsiDocTagValue)findChildByRoleAsPsiElement(ChildRole.DOC_TAG_VALUE);
  }

  public String getName() {
    final PsiElement nameElement = getNameElement();
    if (nameElement == null) return "";
    return nameElement.getText().substring(1);
  }

  public int getChildRole(TreeElement child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == DOC_TAG_NAME) {
      return ChildRole.DOC_TAG_NAME;
    }
    else if (i == DOC_COMMENT_TEXT || i == DOC_INLINE_TAG) {
      return ChildRole.DOC_CONTENT;
    }
    else if (i == DOC_INLINE_TAG_START) {
      return ChildRole.DOC_INLINE_TAG_START;
    }
    else if (i == DOC_INLINE_TAG_END) {
      return ChildRole.DOC_INLINE_TAG_END;
    }
    else if (i == DOC_TAG_VALUE_TOKEN) {
      return ChildRole.DOC_TAG_VALUE;
    }
    else if (i == DOC_METHOD_OR_FIELD_REF) {
      return ChildRole.DOC_TAG_VALUE;
    }
    else {
      return ChildRole.NONE;
    }
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitInlineDocTag(this);
  }

  public String toString() {
    PsiElement nameElement = getNameElement();
    return "PsiInlineDocTag:" + (nameElement != null ? nameElement.getText() : null);
  }

  public PsiElement setName(String name) throws IncorrectOperationException {
    SharedPsiElementImplUtil.setName(getNameElement(), name);
    return this;
  }
}