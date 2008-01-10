package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.SlaveRepositoryPsiElement;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.SrcRepositoryPsiElement;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.RepositoryTreeElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class PsiTypeParameterExtendsBoundsListImpl extends SlaveRepositoryPsiElement implements PsiReferenceList {
  private volatile PsiClassType[] myCachedTypes;

  public PsiTypeParameterExtendsBoundsListImpl(PsiManagerEx manager, RepositoryTreeElement treeElement) {
    super(manager, treeElement);
  }

  public PsiTypeParameterExtendsBoundsListImpl(PsiManagerEx manager, SrcRepositoryPsiElement owner) {
    super(manager, owner);
  }

  protected Object clone() {
    PsiTypeParameterExtendsBoundsListImpl clone = (PsiTypeParameterExtendsBoundsListImpl) super.clone();
    clone.dropCached();
    return clone;
  }

  public void setOwner(SrcRepositoryPsiElement owner) {
    super.setOwner(owner);
    dropCached();
  }

  private void dropCached() {
    myCachedTypes = null;
  }

  @NotNull
  public PsiJavaCodeReferenceElement[] getReferenceElements() {
    return calcTreeElement().getChildrenAsPsiElements(Constants.JAVA_CODE_REFERENCE_BIT_SET,
                                                      Constants.PSI_REFERENCE_ELEMENT_ARRAY_CONSTRUCTOR);
  }

  @NotNull
  public PsiClassType[] getReferencedTypes() {
    if (myCachedTypes == null) {
      long repositoryId = getRepositoryId();
      if (getTreeElement() == null && repositoryId >= 0) {
        myCachedTypes = createTypes(getMirrorElement().getReferenceElements());
      }
      else {
        myCachedTypes = createTypes(getReferenceElements());
      }
    }
    return myCachedTypes;
  }

  private PsiClassType[] createTypes(final PsiJavaCodeReferenceElement[] refs) {
    final PsiElementFactory factory = JavaPsiFacade.getInstance(getManager().getProject()).getElementFactory();
    PsiClassType[] types = new PsiClassType[refs.length];
    for (int i = 0; i < refs.length; i++) {
      types[i] = factory.createType(refs[i]);
    }
    return types;
  }

  private ASTNode getMirrorTreeElement() {
    CompositeElement mirror = ((PsiTypeParameterImpl) getParent()).getMirrorTreeElement();
    ASTNode myselfTree = mirror.findChildByRole(ChildRole.EXTENDS_LIST);
    return myselfTree;
  }

  private PsiTypeParameterExtendsBoundsListImpl getMirrorElement() {
    final ASTNode treeElement = getMirrorTreeElement();
    return (PsiTypeParameterExtendsBoundsListImpl)SourceTreeToPsiMap.treeElementToPsi(treeElement);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitReferenceList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiElement(EXTENDS_BOUND_LIST)";
  }

  public void treeElementSubTreeChanged() {
    super.treeElementSubTreeChanged();
    dropCached();
  }
}
