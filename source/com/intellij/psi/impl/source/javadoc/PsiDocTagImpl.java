package com.intellij.psi.impl.source.javadoc;

import com.intellij.lang.ASTNode;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.PsiElementArrayConstructor;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class PsiDocTagImpl extends CompositePsiElement implements PsiDocTag, Constants {

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
    return getChildrenAsPsiElements(VALUE_BIT_SET, PsiElementArrayConstructor.PSI_ELEMENT_ARRAY_CONSTRUCTOR);
  }

  public String getName() {
    if (getNameElement() == null) return "";
    return getNameElement().getText().substring(1);
  }

  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    PsiImplUtil.setName(getNameElement(), name);
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
      return ChildRoleBase.NONE;
    }
  }

  @NotNull
  public PsiReference[] getReferences() {
    return ReferenceProvidersRegistry.getReferencesFromProviders(this, PsiDocTag.class);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitDocTag(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiDocTag:" + getNameElement().getText();
  }
}