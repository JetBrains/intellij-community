package com.intellij.psi.impl.source;

import com.intellij.psi.*;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.impl.source.parsing.Parsing;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class PsiAnonymousClassImpl extends PsiClassImpl implements PsiAnonymousClass {
  private PsiClassType myCachedBaseType = null;

  public PsiAnonymousClassImpl(PsiManagerEx manager, long repositoryId) {
    super(manager, repositoryId);
  }

  public PsiAnonymousClassImpl(PsiManagerEx manager, RepositoryTreeElement treeElement) {
    super(manager, treeElement);
  }

  protected Object clone() {
    PsiAnonymousClassImpl clone = (PsiAnonymousClassImpl)super.clone();
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
    return (PsiExpressionList)calcTreeElement().findChildByRoleAsPsiElement(ChildRole.ARGUMENT_LIST);
  }

  @NotNull
  public PsiJavaCodeReferenceElement getBaseClassReference() {
    return (PsiJavaCodeReferenceElement)calcTreeElement().findChildByRoleAsPsiElement(ChildRole.BASE_CLASS_REFERENCE);
  }

  @NotNull
  public PsiClassType getBaseClassType() {
    // Only do caching if no tree element is avaliable. Otherwise we're in danger to leak tree element via cached type.
    synchronized (PsiLock.LOCK) {
      if (getTreeElement() != null) {
        myCachedBaseType = null;
        return getTypeByTree();
      }

      if (myCachedBaseType == null) {
        final PsiJavaCodeReferenceElement ref;
        long repositoryId = getRepositoryId();
        String refText = getRepositoryManager().getClassView().getBaseClassReferenceText(repositoryId);
        boolean isInQualifiedNew = getRepositoryManager().getClassView().isInQualifiedNew(repositoryId);
        if (!isInQualifiedNew) {
          final DummyHolder holder = new DummyHolder(myManager, calcBasesResolveContext(PsiNameHelper.getShortClassName(refText), getExtendsList()));
          final FileElement holderElement = holder.getTreeElement();
          ref = (PsiJavaCodeReferenceElementImpl)Parsing.parseJavaCodeReferenceText(myManager, refText,
                                                                                    holderElement.getCharTable());
          if (ref == null) {
            myCachedBaseType = PsiClassType.getJavaLangObject(getManager(), getResolveScope());
            return myCachedBaseType;
          }

          TreeUtil.addChildren(holderElement, (TreeElement)ref);
          ((PsiJavaCodeReferenceElementImpl)ref).setKindWhenDummy(PsiJavaCodeReferenceElementImpl.CLASS_NAME_KIND);
        }
        else {
          return getTypeByTree();
        }

        myCachedBaseType = myManager.getElementFactory().createType(ref);
      }
      return myCachedBaseType;
    }
  }

  private PsiClassType getTypeByTree() {
    return myManager.getElementFactory().createType(getBaseClassReference());
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

  public boolean hasModifierProperty(@NotNull String name) {
    return name.equals(PsiModifier.FINAL);
  }

  public PsiReferenceList getExtendsList() {
    return null;
  }

  public PsiReferenceList getImplementsList() {
    return null;
  }

  @NotNull
  public PsiClassType[] getSuperTypes() {
    return PsiClassImplUtil.getSuperTypes(this);
  }

  public PsiClass getContainingClass() {
    return null;
  }

  @NotNull
  public Collection<HierarchicalMethodSignature> getVisibleSignatures() {
    return PsiSuperMethodImplUtil.getVisibleSignatures(this);
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

  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitAnonymousClass(this);
  }

  public String toString() {
    return "PsiAnonymousClass";
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull PsiSubstitutor substitutor,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    if (lastParent instanceof PsiExpressionList) return true;
    if (lastParent != null/* IMPORTANT: do not call getBaseClassReference() for lastParent == null - loads tree!*/
        && lastParent == getBaseClassReference()) {
      return true;
    }
    return super.processDeclarations(processor, substitutor, lastParent, place);
  }

  public void treeElementSubTreeChanged() {
    myCachedBaseType = null;
    super.treeElementSubTreeChanged();
  }
}
