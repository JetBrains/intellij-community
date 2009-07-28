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
import com.intellij.codeInsight.daemon.impl.analysis.AnnotationsHighlightUtil;
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

  public <T extends PsiAnnotationMemberValue>  T setDeclaredAttributeValue(@NonNls String attributeName, @Nullable T value) {
    final PsiAnnotationMemberValue existing = findDeclaredAttributeValue(attributeName);
    if (value == null) {
      if (existing == null) {
        return null;
      }
      existing.getParent().delete();
    } else {
      if (existing != null) {
        ((PsiNameValuePair)existing.getParent()).setValue(value);
      } else {
        final PsiNameValuePair[] attributes = getParameterList().getAttributes();
        if (attributes.length == 1 && attributes[0].getName() == null) {
          attributes[0].replace(createNameValuePair(attributes[0].getValue(), DEFAULT_REFERENCED_METHOD_NAME + "="));
        }

        boolean allowNoName = attributes.length == 0 && "value".equals(attributeName);
        final String namePrefix;
        if (allowNoName) {
          namePrefix = "";
        } else {
          namePrefix = attributeName + "=";
        }
        getParameterList().addBefore(createNameValuePair(value, namePrefix), null);
      }
    }
    return (T)findDeclaredAttributeValue(attributeName);
  }

  private PsiNameValuePair createNameValuePair(PsiAnnotationMemberValue value, String namePrefix) {
    final PsiAnnotation newAnno = JavaPsiFacade.getInstance(getProject()).getElementFactory()
      .createAnnotationFromText("@A(" + namePrefix + value.getText() + ")", null);
    return newAnno.getParameterList().getAttributes()[0];
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

  public PsiAnnotationOwner getOwner() {
    PsiElement parent = getParent();
    if (parent instanceof PsiTypeElement) {
      return ((PsiTypeElement)parent).getOwner(this);
    }
    if (parent instanceof PsiMethodReceiver || parent instanceof PsiTypeParameter) return (PsiAnnotationOwner)parent;
    PsiElement member = parent.getParent();
    String[] elementTypeFields = AnnotationsHighlightUtil.getApplicableElementTypeFields(member);
    if (elementTypeFields == null) return null;
    if (AnnotationsHighlightUtil.isAnnotationApplicableTo(this, true, elementTypeFields)) return (PsiAnnotationOwner)parent;

    PsiAnnotationOwner typeElement;
    if (member instanceof PsiVariable) {
      typeElement = ((PsiVariable)member).getTypeElement();
    }
    else if (member instanceof PsiMethod) {
      typeElement = ((PsiMethod)member).getReturnTypeElement();
    }
    else if (parent instanceof PsiAnnotationOwner) {
      typeElement = (PsiAnnotationOwner)parent;
    }
    else {
      typeElement = null;
    }
    return typeElement;
  }
}