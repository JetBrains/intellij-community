package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.SlaveRepositoryPsiElement;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.SrcRepositoryPsiElement;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.RepositoryTreeElement;

/**
 * @author max
 */
public class PsiTypeParameterExtendsBoundsListImpl extends SlaveRepositoryPsiElement implements PsiReferenceList {
  private PsiClassType[] myCachedTypes;

  public PsiTypeParameterExtendsBoundsListImpl(PsiManagerImpl manager, RepositoryTreeElement treeElement) {
    super(manager, treeElement);
  }

  public PsiTypeParameterExtendsBoundsListImpl(PsiManagerImpl manager, SrcRepositoryPsiElement owner) {
    super(manager, owner);
  }

  protected Object clone() {
    PsiTypeParameterExtendsBoundsListImpl clone = (PsiTypeParameterExtendsBoundsListImpl) super.clone();
    clone.myCachedTypes = null;
    return clone;
  }

  public void setOwner(SrcRepositoryPsiElement owner) {
    super.setOwner(owner);
    myCachedTypes = null;
  }

  public PsiJavaCodeReferenceElement[] getReferenceElements() {
    return (PsiJavaCodeReferenceElement[]) calcTreeElement().getChildrenAsPsiElements(Constants.JAVA_CODE_REFERENCE_BIT_SET,
                                                                                      PSI_REFERENCE_ELEMENT_ARRAY_CONSTRUCTOR);
  }

  public PsiClassType[] getReferencedTypes() {
    synchronized (PsiLock.LOCK) {
      if (myCachedTypes == null) {
        long repositoryId = getRepositoryId();
        if (getTreeElement() == null && repositoryId >= 0) {
          myCachedTypes = createTypes(getMirrorElement().getReferenceElements());
        }
        else {
          return createTypes(getReferenceElements());
        }
      }
      return myCachedTypes;
    }
  }

  private PsiClassType[] createTypes(final PsiJavaCodeReferenceElement[] refs) {
    final PsiElementFactory factory = getManager().getElementFactory();
    PsiClassType[] types = new PsiClassType[refs.length];
    for (int i = 0; i < refs.length; i++) {
      types[i] = factory.createType(refs[i]);
    }
    return types;
  }

  private CompositeElement getMirrorTreeElement() {
    CompositeElement mirror = ((PsiTypeParameterImpl) getParent()).getMirrorTreeElement();
    CompositeElement myselfTree = (CompositeElement) mirror.findChildByRole(ChildRole.EXTENDS_LIST);
    return myselfTree;
  }

  private PsiTypeParameterExtendsBoundsListImpl getMirrorElement() {
    final CompositeElement treeElement = getMirrorTreeElement();
    return (PsiTypeParameterExtendsBoundsListImpl)SourceTreeToPsiMap.treeElementToPsi(treeElement);
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitReferenceList(this);
  }

  public String toString() {
    return "PsiElement(EXTENDS_BOUND_LIST)";
  }

  public void treeElementSubTreeChanged() {
    super.treeElementSubTreeChanged();
    myCachedTypes = null;
  }
}
