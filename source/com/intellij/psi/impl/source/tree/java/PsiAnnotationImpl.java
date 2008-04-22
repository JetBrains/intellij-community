package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiAnnotationStub;
import com.intellij.psi.impl.meta.MetaRegistry;
import com.intellij.psi.impl.source.JavaStubPsiElement;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.meta.PsiMetaData;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */
public class PsiAnnotationImpl extends JavaStubPsiElement<PsiAnnotationStub> implements PsiAnnotation {
  public PsiAnnotationImpl(final PsiAnnotationStub stub) {
    super(stub, JavaStubElementTypes.ANNOTATION);
  }

  public PsiAnnotationImpl(final ASTNode node) {
    super(node);
  }

  public PsiJavaCodeReferenceElement getNameReferenceElement() {
    return (PsiJavaCodeReferenceElement)getMirrorTreeElement().findChildByRoleAsPsiElement(ChildRole.CLASS_REFERENCE);
  }


  private CompositeElement getMirrorTreeElement() {
    final PsiAnnotationStub stub = getStub();
    if (stub != null) {
      return stub.getTreeElement();
    }

    return (CompositeElement)getNode();
  }

  public PsiAnnotationMemberValue findAttributeValue(String attributeName) {
    return PsiImplUtil.findAttributeValue(this, attributeName);
  }

  @Nullable
  public PsiAnnotationMemberValue findDeclaredAttributeValue(@NonNls final String attributeName) {
    return PsiImplUtil.findDeclaredAttributeValue(this, attributeName);
  }

  public String toString() {
    return "PsiAnnotation";
  }

  @NotNull
  public PsiAnnotationParameterList getParameterList() {
    return (PsiAnnotationParameterList)getMirrorTreeElement().findChildByRoleAsPsiElement(ChildRole.PARAMETER_LIST);
  }

  @Nullable public String getQualifiedName() {
    final PsiJavaCodeReferenceElement nameRef = getNameReferenceElement();
    if (nameRef == null) return null;
    return nameRef.getCanonicalText();
  }

  public final void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitAnnotation(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public PsiMetaData getMetaData() {
    return MetaRegistry.getMetaBase(this);
  }
}