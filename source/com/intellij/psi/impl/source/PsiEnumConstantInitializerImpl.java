package com.intellij.psi.impl.source;

import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.light.LightClassReference;
import com.intellij.psi.impl.source.parsing.Parsing;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.RepositoryTreeElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.openapi.diagnostic.Logger;

public class PsiEnumConstantInitializerImpl extends PsiClassImpl implements PsiEnumConstantInitializer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiEnumConstantInitializerImpl");
  private PsiClassType myCachedBaseType = null;

  public PsiEnumConstantInitializerImpl(PsiManagerImpl manager, long repositoryId) {
    super(manager, repositoryId);
  }

  public PsiEnumConstantInitializerImpl(PsiManagerImpl manager, RepositoryTreeElement treeElement) {
    super(manager, treeElement);
  }

  protected Object clone() {
    PsiEnumConstantInitializerImpl clone = (PsiEnumConstantInitializerImpl)super.clone();
    clone.myCachedBaseType = null;
    return clone;
  }

  public void setRepositoryId(long repositoryId) {
    super.setRepositoryId(repositoryId);
  }

  public void subtreeChanged() {
    super.subtreeChanged();
    myCachedBaseType = null;
  }

  public PsiExpressionList getArgumentList() {
    PsiElement parent = getParent();
    LOG.assertTrue(parent instanceof PsiEnumConstant);
    return ((PsiEnumConstant)parent).getArgumentList();
  }

  public PsiJavaCodeReferenceElement getBaseClassReference() {
    PsiClass containingClass = getBaseClass();
    return new LightClassReference(getManager(), containingClass.getName(), containingClass);
  }

  private PsiClass getBaseClass() {
    PsiElement parent = getParent();
    LOG.assertTrue(parent instanceof PsiEnumConstant);
    PsiClass containingClass = ((PsiEnumConstant)parent).getContainingClass();
    LOG.assertTrue(containingClass != null);
    return containingClass;
  }

  public PsiElement getParent() {
    return getDefaultParentByRepository();
  }

  public PsiEnumConstant getEnumConstant() {
    return (PsiEnumConstant) getParent();
  }

  public PsiClassType getBaseClassType() {
    if (myCachedBaseType == null) {
      myCachedBaseType = myManager.getElementFactory().createType(getBaseClass());
    }
    return myCachedBaseType;
  }

  public PsiIdentifier getNameIdentifier() {
    return null;
  }

  public String getQualifiedName() {
    return null;
  }

  public PsiModifierList getModifierList() {
    return null;
  }

  public boolean hasModifierProperty(String name) {
    return false;
  }

  public PsiReferenceList getExtendsList() {
    return null;
  }

  public PsiReferenceList getImplementsList() {
    return null;
  }

  public PsiClassType[] getSuperTypes() {
    return new PsiClassType[]{getBaseClassType()};
  }

  public boolean isInterface() {
    return false;
  }

  public boolean isAnnotationType() {
    return false;
  }

  public boolean isEnum() {
    return false;
  }

  public PsiTypeParameterList getTypeParameterList() {
    return null;
  }

  public PsiElement getOriginalElement() {
    return this;
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitEnumConstantInitializer(this);
  }

  public String toString() {
    return "PsiAnonymousClass (PsiEnumConstantInitializerImpl)):";
  }

  public void treeElementSubTreeChanged() {
    myCachedBaseType = null;
    super.treeElementSubTreeChanged();
  }
}
