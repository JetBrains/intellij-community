package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.java.stubs.PsiAnnotationStub;
import com.intellij.psi.impl.meta.MetaRegistry;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.meta.PsiMetaData;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */
public class ClsAnnotationImpl extends ClsRepositoryPsiElement<PsiAnnotationStub> implements PsiAnnotation, Navigatable {
  public static final ClsAnnotationImpl[] EMPTY_ARRAY = new ClsAnnotationImpl[0];
  private static final Logger LOG = Logger.getInstance("com.intellij.psi.impl.compiled.ClsAnnotationImpl");
  private ClsJavaCodeReferenceElementImpl myReferenceElement;
  private ClsAnnotationParameterListImpl myParameterList;
  private boolean myIsInitialized = false;

  public ClsAnnotationImpl(final PsiAnnotationStub stub) {
    super(stub);
  }

  private void init() {
    if (myIsInitialized) return;
    myIsInitialized = true;

    final PsiAnnotationStub stub = getStub();
    final CompositeElement mirror = stub.getTreeElement();
    myReferenceElement = new ClsJavaCodeReferenceElementImpl(this, mirror.findChildByRole(ChildRole.CLASS_REFERENCE).getText());

    final PsiAnnotationParameterList paramList = (PsiAnnotationParameterList)mirror.findChildByRoleAsPsiElement(ChildRole.PARAMETER_LIST);

    myParameterList = new ClsAnnotationParameterListImpl(this);
    PsiNameValuePair[] psiAttributes = paramList.getAttributes();
    ClsNameValuePairImpl[] attributes = new ClsNameValuePairImpl[psiAttributes.length];
    myParameterList.setAttributes(attributes);
    for (int i = 0; i < attributes.length; i++) {
      attributes[i] = new ClsNameValuePairImpl(myParameterList);
      attributes[i].setNameIdentifier(new ClsIdentifierImpl(attributes[i], psiAttributes[i].getName()));
      attributes[i].setMemberValue(ClsAnnotationsUtil.getMemberValue(psiAttributes[i].getValue(), attributes[i]));
    }
  }

  public void appendMirrorText(final int indentLevel, final StringBuffer buffer) {
    init();
    buffer.append("@").append(myReferenceElement.getCanonicalText());
    myParameterList.appendMirrorText(indentLevel, buffer);
  }

  public void setMirror(@NotNull TreeElement element) {
    LOG.assertTrue(isValid());
    LOG.assertTrue(!CHECK_MIRROR_ENABLED || myMirror == null);
    myMirror = element;

    PsiAnnotation mirror = (PsiAnnotation)SourceTreeToPsiMap.treeElementToPsi(element);
      ((ClsElementImpl)getParameterList()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getParameterList()));
      ((ClsElementImpl)getNameReferenceElement()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getNameReferenceElement()));
  }

  @NotNull
  public PsiElement[] getChildren() {
    init();
    return new PsiElement[]{myReferenceElement, myParameterList};
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitAnnotation(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @NotNull
  public PsiAnnotationParameterList getParameterList() {
    init();
    return myParameterList;
  }

  @Nullable public String getQualifiedName() {
    init();
    if (myReferenceElement == null) return null;
    return myReferenceElement.getCanonicalText();
  }

  public PsiJavaCodeReferenceElement getNameReferenceElement() {
    init();
    return myReferenceElement;
  }

  public PsiAnnotationMemberValue findAttributeValue(String attributeName) {
    return PsiImplUtil.findAttributeValue(this, attributeName);
  }

  @Nullable
  public PsiAnnotationMemberValue findDeclaredAttributeValue(@NonNls final String attributeName) {
    return PsiImplUtil.findDeclaredAttributeValue(this, attributeName);
  }

  public String getText() {
    final StringBuffer buffer = new StringBuffer();
    appendMirrorText(0, buffer);
    return buffer.toString();
  }

  public PsiMetaData getMetaData() {
    return MetaRegistry.getMetaBase(this);
  }
}
