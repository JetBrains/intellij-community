package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.javadoc.PsiInlineDocTag;
import com.intellij.psi.impl.SharedPsiElementImplUtil;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class PsiInlineDocTagImpl extends CompositePsiElement implements PsiInlineDocTag {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiInlineDocTagImpl");

  private static final TokenSet VALUE_BIT_SET = TokenSet.create(new IElementType[]{
    JAVA_CODE_REFERENCE,
    DOC_TAG_VALUE_TOKEN,
    DOC_METHOD_OR_FIELD_REF,
    DOC_COMMENT_DATA,
    DOC_INLINE_TAG,
    DOC_REFERENCE_HOLDER
  });

  public PsiInlineDocTagImpl() {
    super(DOC_INLINE_TAG);
  }

  public PsiDocComment getContainingComment() {
    ASTNode scope = getTreeParent();
    while (scope.getElementType() != JavaDocElementType.DOC_COMMENT) {
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

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == DOC_TAG_NAME) {
      return ChildRole.DOC_TAG_NAME;
    }
    else if (i == JavaDocElementType.DOC_COMMENT || i == DOC_INLINE_TAG) {
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

  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitInlineDocTag(this);
  }

  public String toString() {
    PsiElement nameElement = getNameElement();
    return "PsiInlineDocTag:" + (nameElement != null ? nameElement.getText() : null);
  }

  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    SharedPsiElementImplUtil.setName(getNameElement(), name);
    return this;
  }
}