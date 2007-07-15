package com.intellij.psi.impl.source;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.java.PomMethod;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.impl.SharedPsiElementImplUtil;
import com.intellij.psi.impl.cache.MethodView;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.impl.source.tree.java.PsiTypeParameterListImpl;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PatchedSoftReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import java.util.List;

public class PsiMethodImpl extends NonSlaveRepositoryPsiElement implements PsiMethod {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiMethodImpl");

  private PsiModifierListImpl myRepositoryModifierList = null;
  private PsiParameterListImpl myRepositoryParameterList = null;
  private PsiReferenceListImpl myRepositoryThrowsList = null;
  private PsiTypeParameterListImpl myRepositoryTypeParameterList;

  @NonNls private String myCachedName = null;
  private Boolean myCachedIsDeprecated = null;
  private Boolean myCachedIsConstructor = null;
  private Boolean myCachedIsVarargs = null;
  private PatchedSoftReference<PsiType> myCachedType = null;

  public PsiMethodImpl(PsiManagerEx manager, long repositoryId) {
    super(manager, repositoryId);
  }

  public PsiMethodImpl(PsiManagerEx manager, RepositoryTreeElement treeElement) {
    super(manager, treeElement);
  }

  public void subtreeChanged() {
    super.subtreeChanged();
    dropCached();
  }

  private void dropCached() {
    myCachedType = null;
    myCachedName = null;
    myCachedIsDeprecated = null;
    myCachedIsConstructor = null;
    myCachedIsVarargs = null;
  }

  protected Object clone() {
    PsiMethodImpl clone = (PsiMethodImpl)super.clone();
    clone.myRepositoryModifierList = null;
    clone.myRepositoryParameterList = null;
    clone.myRepositoryTypeParameterList = null;
    clone.myRepositoryThrowsList = null;
    clone.dropCached();
    return clone;
  }

  public void setRepositoryId(long repositoryId) {
    super.setRepositoryId(repositoryId);

    if (repositoryId < 0){
      if (myRepositoryModifierList != null){
        myRepositoryModifierList.setOwner(this);
        myRepositoryModifierList = null;
      }
      if (myRepositoryParameterList != null){
        myRepositoryParameterList.setOwner(this);
        myRepositoryParameterList = null;
      }
      if (myRepositoryTypeParameterList != null){
        myRepositoryTypeParameterList.setOwner(this);
        myRepositoryTypeParameterList = null;
      }
      if (myRepositoryThrowsList != null){
        myRepositoryThrowsList.setOwner(this);
        myRepositoryThrowsList = null;
      }
    }
    else{
      myRepositoryModifierList = (PsiModifierListImpl)bindSlave(ChildRole.MODIFIER_LIST);
      myRepositoryParameterList = (PsiParameterListImpl)bindSlave(ChildRole.PARAMETER_LIST);
      myRepositoryTypeParameterList = (PsiTypeParameterListImpl) bindSlave(ChildRole.TYPE_PARAMETER_LIST);
      myRepositoryThrowsList = (PsiReferenceListImpl)bindSlave(ChildRole.THROWS_LIST);
    }
    dropCached();
  }

  public PsiClass getContainingClass() {
    PsiElement parent = getParent();
    return parent instanceof PsiClass ? (PsiClass)parent : PsiTreeUtil.getParentOfType(this, JspClass.class);
  }

  public PsiIdentifier getNameIdentifier() {
    return (PsiIdentifier)calcTreeElement().findChildByRoleAsPsiElement(ChildRole.NAME);
  }

  @NotNull
  public PsiMethod[] findSuperMethods() {
    return PsiSuperMethodImplUtil.findSuperMethods(this);
  }

  @NotNull
  public PsiMethod[] findSuperMethods(boolean checkAccess) {
    return PsiSuperMethodImplUtil.findSuperMethods(this, checkAccess);
  }

  @NotNull
  public PsiMethod[] findSuperMethods(PsiClass parentClass) {
    return PsiSuperMethodImplUtil.findSuperMethods(this, parentClass);
  }

  @NotNull
  public List<MethodSignatureBackedByPsiMethod> findSuperMethodSignaturesIncludingStatic(boolean checkAccess) {
    return PsiSuperMethodImplUtil.findSuperMethodSignaturesIncludingStatic(this, checkAccess);
  }

  public PsiMethod findDeepestSuperMethod() {
    return PsiSuperMethodImplUtil.findDeepestSuperMethod(this);
  }

  @NotNull
  public PsiMethod[] findDeepestSuperMethods() {
    return PsiSuperMethodImplUtil.findDeepestSuperMethods(this);
  }

  public PomMethod getPom() {
    //TODO:
    return null;
  }

  @NotNull
  public String getName() {
    @NonNls String name = myCachedName;
    if (name == null){
      if (getTreeElement() != null){
        final PsiIdentifier nameIdentifier = getNameIdentifier();
        name = nameIdentifier == null ? "<unnamed>" : nameIdentifier.getText();
      }
      else{
        name = getRepositoryManager().getMethodView().getName(getRepositoryId());
      }
      myCachedName = name;
    }
    return name;
  }

  @NotNull
  public HierarchicalMethodSignature getHierarchicalMethodSignature() {
    return PsiSuperMethodImplUtil.getHierarchicalMethodSignature(this);
  }

  public PsiElement setName(@NotNull String name) throws IncorrectOperationException{
    SharedPsiElementImplUtil.setName(getNameIdentifier(), name);
    return this;
  }

  public PsiTypeElement getReturnTypeElement() {
    if (myCachedIsConstructor == Boolean.TRUE) return null;
    return (PsiTypeElement)calcTreeElement().findChildByRoleAsPsiElement(ChildRole.TYPE);
  }

  public PsiTypeParameterList getTypeParameterList() {
    long repositoryId = getRepositoryId();
    if (repositoryId >= 0){
      if (myRepositoryTypeParameterList == null){
        myRepositoryTypeParameterList = new PsiTypeParameterListImpl(myManager, this);
      }
      return myRepositoryTypeParameterList;
    }

    return (PsiTypeParameterList) calcTreeElement().findChildByRoleAsPsiElement(ChildRole.TYPE_PARAMETER_LIST);
  }

  public boolean hasTypeParameters() {
    return PsiImplUtil.hasTypeParameters(this);
  }

  @NotNull public PsiTypeParameter[] getTypeParameters() {
    return PsiImplUtil.getTypeParameters(this);
  }

  public PsiType getReturnType() {
    if (myCachedIsConstructor == Boolean.TRUE) return null;

    if (getTreeElement() != null) {
      myCachedType = null;

      PsiTypeElement typeElement = getReturnTypeElement();
      if (typeElement == null) return null;
      PsiParameterList parameterList = getParameterList();
      return SharedImplUtil.getType(typeElement, parameterList, this);
    }
    else{
      if (myCachedType != null) {
        PsiType type = myCachedType.get();
        if (type != null) return type;
      }

      String typeText = getRepositoryManager().getMethodView().getReturnTypeText(getRepositoryId());
      if (typeText == null) return null;

      try{
        final PsiType type = getManager().getElementFactory().createTypeFromText(typeText, this);
        myCachedType = new PatchedSoftReference<PsiType>(type);
        return type;
      }
      catch(IncorrectOperationException e){
        LOG.error(e);
        return null;
      }
    }
  }

  @NotNull
  public PsiModifierList getModifierList() {
    if (getRepositoryId() >= 0){
      if (myRepositoryModifierList == null){
        myRepositoryModifierList = new PsiModifierListImpl(myManager, this);
      }
      return myRepositoryModifierList;
    }
    else{
      return (PsiModifierList)calcTreeElement().findChildByRoleAsPsiElement(ChildRole.MODIFIER_LIST);
    }
  }

  public boolean hasModifierProperty(@NotNull String name) {
    return getModifierList().hasModifierProperty(name);
  }

  @NotNull
  public PsiParameterList getParameterList() {
    if (getRepositoryId() >= 0){
      if (myRepositoryParameterList == null){
        myRepositoryParameterList = new PsiParameterListImpl(myManager, this);
      }
      return myRepositoryParameterList;
    }
    else{
      return (PsiParameterList)calcTreeElement().findChildByRoleAsPsiElement(ChildRole.PARAMETER_LIST);
    }
  }

  @NotNull
  public PsiReferenceList getThrowsList() {
    if (getRepositoryId() >= 0){
      if (myRepositoryThrowsList == null){
        myRepositoryThrowsList = new PsiReferenceListImpl(myManager, this, JavaElementType.THROWS_LIST);
      }
      return myRepositoryThrowsList;
    }
    else{
      final CompositeElement compositeElement = calcTreeElement();
      return (PsiReferenceList)compositeElement.findChildByRoleAsPsiElement(ChildRole.THROWS_LIST);
    }
  }

  public PsiCodeBlock getBody() {
    return (PsiCodeBlock)calcTreeElement().findChildByRoleAsPsiElement(ChildRole.METHOD_BODY);
  }

  public boolean isDeprecated() {
    Boolean deprecated = myCachedIsDeprecated;
    if (deprecated == null){
      if (getTreeElement() != null){
        PsiDocComment docComment = getDocComment();
        deprecated = docComment != null && docComment.findTagByName("deprecated") != null;
        if (!deprecated) {
          deprecated = getModifierList().findAnnotation("java.lang.Deprecated") != null;
        }
      }
      else{
        MethodView methodView = getRepositoryManager().getMethodView();
        deprecated = methodView.isDeprecated(getRepositoryId());
        if (!deprecated && methodView.mayBeDeprecatedByAnnotation(getRepositoryId())) {
          deprecated = getModifierList().findAnnotation("java.lang.Deprecated") != null;
        }
      }
      myCachedIsDeprecated = deprecated;
    }
    return deprecated.booleanValue();
  }

  public PsiDocComment getDocComment() {
    return (PsiDocComment)calcTreeElement().findChildByRoleAsPsiElement(ChildRole.DOC_COMMENT);
  }

  public boolean isConstructor() {
    Boolean isConstructor = myCachedIsConstructor;
    if (isConstructor == null){
      if (getTreeElement() != null){
        isConstructor = calcTreeElement().findChildByRole(ChildRole.TYPE) == null;
      }
      else{
        isConstructor = getRepositoryManager().getMethodView().isConstructor(getRepositoryId());
      }
      myCachedIsConstructor = isConstructor;
    }
    return isConstructor.booleanValue();
  }

  public boolean isVarArgs() {
    Boolean isVarArgs = myCachedIsVarargs;
    if (isVarArgs == null) {
      if (getTreeElement() != null) {
        PsiParameter[] parameters = getParameterList().getParameters();
        isVarArgs = parameters.length > 0 && parameters[parameters.length - 1].isVarArgs();
      }
      else {
        isVarArgs = getRepositoryManager().getMethodView().isVarArgs(getRepositoryId());
      }

      myCachedIsVarargs = isVarArgs;
    }

    return isVarArgs.booleanValue();
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitMethod(this);
  }

  public String toString() {
    return "PsiMethod:" + getName();
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull PsiSubstitutor substitutor, PsiElement lastParent, @NotNull PsiElement place) {
    return PsiImplUtil.processDeclarationsInMethod(this, processor, substitutor, lastParent, place);

  }

  @NotNull
  public MethodSignature getSignature(@NotNull PsiSubstitutor substitutor){
    return MethodSignatureBackedByPsiMethod.create(this, substitutor);
  }

  public void treeElementSubTreeChanged() {
    myCachedType = null;
    myCachedName = null;
    myCachedIsDeprecated = null;
    myCachedIsConstructor = null;
    myRepositoryThrowsList = null;
    myRepositoryModifierList = null;
    myRepositoryParameterList = null;
    myRepositoryTypeParameterList = null;
    super.treeElementSubTreeChanged();
  }

  public PsiElement getOriginalElement() {
    PsiClass originalClass = (PsiClass)getContainingClass().getOriginalElement();
    final PsiMethod originalMethod = originalClass.findMethodBySignature(this, false);
    return originalMethod != null ? originalMethod : this;
  }

  public ItemPresentation getPresentation() {
    return SymbolPresentationUtil.getMethodPresentation(this);
  }
}

