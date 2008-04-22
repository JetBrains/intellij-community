package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.impl.java.stubs.PsiClassStub;
import com.intellij.psi.impl.source.parsing.Parsing;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.PatchedSoftReference;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class PsiAnonymousClassImpl extends PsiClassImpl implements PsiAnonymousClass {
  private PatchedSoftReference<PsiClassType> myCachedBaseType = null;

  public PsiAnonymousClassImpl(final PsiClassStub stub) {
    super(stub);
  }

  public PsiAnonymousClassImpl(final ASTNode node) {
    super(node);
  }

  protected Object clone() {
    PsiAnonymousClassImpl clone = (PsiAnonymousClassImpl)super.clone();
    clone.myCachedBaseType = null;
    return clone;
  }

  public void subtreeChanged() {
    super.subtreeChanged();
    myCachedBaseType = null;
  }

  public PsiExpressionList getArgumentList() {
    return (PsiExpressionList)getNode().findChildByRoleAsPsiElement(ChildRole.ARGUMENT_LIST);
  }

  @NotNull
  public PsiJavaCodeReferenceElement getBaseClassReference() {
    return (PsiJavaCodeReferenceElement)getNode().findChildByRoleAsPsiElement(ChildRole.BASE_CLASS_REFERENCE);
  }

  @NotNull
  public PsiClassType getBaseClassType() {
    final PsiClassStub stub = getStub();
    if (stub == null) {
      myCachedBaseType = null;
      return getTypeByTree();
    }

    PsiClassType type = null;
    if (myCachedBaseType != null) type = myCachedBaseType.get();
    if (type != null) return type;

    if (!isInQualifiedNew()) {
      final PsiJavaCodeReferenceElement ref;
      String refText = stub.getBaseClassReferenceText();
      final DummyHolder holder = DummyHolderFactory
        .createHolder(getManager(), calcBasesResolveContext(PsiNameHelper.getShortClassName(refText), getExtendsList()));
      final FileElement holderElement = holder.getTreeElement();

      ref = (PsiJavaCodeReferenceElementImpl)Parsing.parseJavaCodeReferenceText(getManager(), refText,
                                                                                holderElement.getCharTable());
      if (ref == null) {
        type = PsiClassType.getJavaLangObject(getManager(), getResolveScope());
        myCachedBaseType = new PatchedSoftReference<PsiClassType>(type);
        return type;
      }

      TreeUtil.addChildren(holderElement, (TreeElement)ref);
      ((PsiJavaCodeReferenceElementImpl)ref).setKindWhenDummy(PsiJavaCodeReferenceElementImpl.CLASS_NAME_KIND);

      type = JavaPsiFacade.getInstance(getProject()).getElementFactory().createType(ref);
      myCachedBaseType = new PatchedSoftReference<PsiClassType>(type);
      return type;
    }
    else {
      return getTypeByTree();
    }
  }

  private PsiClassType getTypeByTree() {
    return JavaPsiFacade.getInstance(getProject()).getElementFactory().createType(getBaseClassReference());
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
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitAnonymousClass(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiAnonymousClass";
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    if (lastParent instanceof PsiExpressionList) return true;
    if (lastParent != null/* IMPORTANT: do not call getBaseClassReference() for lastParent == null - loads tree!*/
        && lastParent == getBaseClassReference()) {
      return true;
    }
    return super.processDeclarations(processor, state, lastParent, place);
  }

  public boolean isInQualifiedNew() {
    final PsiClassStub stub = getStub();
    if (stub != null) {
      return stub.isAnonymousInQualifiedNew();
    }

    final PsiElement parent = getParent();
    return parent instanceof PsiNewExpression && ((PsiNewExpression)parent).getQualifier() != null;
  }

  public PsiElement getParent() {
    return SharedImplUtil.getParent(getNode());
  }
}