package com.intellij.psi.impl.source;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.java.PomMethod;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.impl.SharedPsiElementImplUtil;
import com.intellij.psi.impl.cache.MethodView;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.impl.source.tree.java.PsiTypeParameterListImpl;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.util.IncorrectOperationException;

import java.util.List;

public class PsiMethodImpl extends NonSlaveRepositoryPsiElement implements PsiMethod {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiMethodImpl");

  private PsiModifierListImpl myRepositoryModifierList = null;
  private PsiParameterListImpl myRepositoryParameterList = null;
  private PsiReferenceListImpl myRepositoryThrowsList = null;
  private PsiTypeParameterListImpl myRepositoryTypeParameterList;

  private String myCachedName = null;
  private Boolean myCachedIsDeprecated = null;
  private Boolean myCachedIsConstructor = null;
  private Boolean myCachedIsVarargs = null;
  private String myCachedTypeText = null;

  public PsiMethodImpl(PsiManagerImpl manager, long repositoryId) {
    super(manager, repositoryId);
  }

  public PsiMethodImpl(PsiManagerImpl manager, RepositoryTreeElement treeElement) {
    super(manager, treeElement);
  }

  public void subtreeChanged() {
    super.subtreeChanged();
    myCachedTypeText = null;
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
  }

  public PsiClass getContainingClass() {
    PsiElement parent = getParent();
    return parent instanceof PsiClass ? (PsiClass)parent : null;
  }

  public PsiIdentifier getNameIdentifier() {
    return (PsiIdentifier)calcTreeElement().findChildByRoleAsPsiElement(ChildRole.NAME);
  }

  public PsiMethod[] findSuperMethods() {
    return PsiSuperMethodImplUtil.findSuperMethods(this);
  }

  public PsiMethod[] findSuperMethods(boolean checkAccess) {
    return PsiSuperMethodImplUtil.findSuperMethods(this, checkAccess);
  }

  public PsiMethod[] findSuperMethods(PsiClass parentClass) {
    return PsiSuperMethodImplUtil.findSuperMethods(this, parentClass);
  }

  public List<MethodSignatureBackedByPsiMethod> findSuperMethodSignaturesIncludingStatic(boolean checkAccess) {
    return PsiSuperMethodImplUtil.findSuperMethodSignaturesIncludingStatic(this, checkAccess);
  }

  public PsiMethod findConstructorInSuper() {
    return PsiSuperMethodImplUtil.findConstructorInSuper(this);
  }

  public PsiMethod findDeepestSuperMethod() {
    return PsiSuperMethodImplUtil.findDeepestSuperMethod(this);
  }

  public PomMethod getPom() {
    //TODO:
    return null;
  }

  public String getName() {
    if (myCachedName == null){
      if (getTreeElement() != null){
        myCachedName = getNameIdentifier().getText();
      }
      else{
        myCachedName = getRepositoryManager().getMethodView().getName(getRepositoryId());
      }
    }
    return myCachedName;
  }

  public PsiElement setName(String name) throws IncorrectOperationException{
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

  public PsiType getReturnType() {
    if (myCachedIsConstructor == Boolean.TRUE) return null;

    if (getTreeElement() != null){
      PsiTypeElement typeElement = getReturnTypeElement();
      if (typeElement == null) return null;

      int arrayCount = 0;
      TreeElement parameterList = SourceTreeToPsiMap.psiElementToTree(getParameterList());
      Loop:
        for(TreeElement child = parameterList.getTreeNext(); child != null; child = child.getTreeNext()){
          IElementType i = child.getElementType();
          if (i == LBRACKET) {
            arrayCount++;
          }
          else if (i == RBRACKET || i == WHITE_SPACE || i == C_STYLE_COMMENT || i == DOC_COMMENT || i == END_OF_LINE_COMMENT) {
          }
          else {
            break Loop;
          }
        }

      PsiType type;
      if (!(typeElement instanceof PsiTypeElementImpl)) {
        type = typeElement.getType();
      }
      else {
        type = ((PsiTypeElementImpl)typeElement).getDetachedType(this);
      }

      for (int i = 0; i < arrayCount; i++) {
        type = type.createArrayType();
      }

      return type;
    }
    else{
      myCachedTypeText = getRepositoryManager().getMethodView().getReturnTypeText(getRepositoryId());
      if (myCachedTypeText == null) return null;
      try{
        return getManager().getElementFactory().createTypeFromText(myCachedTypeText, this);
      }
      catch(IncorrectOperationException e){
        LOG.error(e);
        return null;
      }
    }
  }

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

  public boolean hasModifierProperty(String name) {
    return getModifierList().hasModifierProperty(name);
  }

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

  public PsiReferenceList getThrowsList() {
    if (getRepositoryId() >= 0){
      if (myRepositoryThrowsList == null){
        myRepositoryThrowsList = new PsiReferenceListImpl(myManager, this, ElementType.THROWS_LIST);
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
    if (myCachedIsDeprecated == null){
      boolean deprecated;
      if (getTreeElement() != null){
        PsiDocComment docComment = getDocComment();
        deprecated = docComment != null && getDocComment().findTagByName("deprecated") != null;
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
      myCachedIsDeprecated = deprecated ? Boolean.TRUE : Boolean.FALSE;
    }
    return myCachedIsDeprecated.booleanValue();
  }

  public PsiDocComment getDocComment() {
    return (PsiDocComment)calcTreeElement().findChildByRoleAsPsiElement(ChildRole.DOC_COMMENT);
  }

  public boolean isConstructor() {
    if (myCachedIsConstructor == null){
      boolean isConstructor;
      if (getTreeElement() != null){
        isConstructor = calcTreeElement().findChildByRole(ChildRole.TYPE) == null;
      }
      else{
        isConstructor = getRepositoryManager().getMethodView().isConstructor(getRepositoryId());
      }
      myCachedIsConstructor = isConstructor ? Boolean.TRUE : Boolean.FALSE;
    }
    return myCachedIsConstructor.booleanValue();
  }

  public boolean isVarArgs() {
    if (myCachedIsVarargs == null) {
      boolean isVarArgs = false;
      if (getTreeElement() != null) {
        PsiParameter[] parameters = getParameterList().getParameters();
        for (int i = parameters.length - 1; i >= 0; i--) {
          if (parameters[i].isVarArgs()) {
            isVarArgs = true;
            break;
          }
        }
      }
      else {
        isVarArgs = getRepositoryManager().getMethodView().isVarArgs(getRepositoryId());
      }

      myCachedIsVarargs = isVarArgs ? Boolean.TRUE : Boolean.FALSE;
    }

    return myCachedIsVarargs.booleanValue();
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitMethod(this);
  }

  public String toString() {
    return "PsiMethod:" + getName();
  }

  public boolean processDeclarations(PsiScopeProcessor processor, PsiSubstitutor substitutor, PsiElement lastParent, PsiElement place) {
    return PsiImplUtil.processDeclarationsInMethod(this, processor, substitutor, lastParent, place);

  }

  public MethodSignature getSignature(PsiSubstitutor substitutor){
    return PsiImplUtil.getMethodSignature(this, substitutor);
  }

  public void treeElementSubTreeChanged() {
    myCachedTypeText = null;
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

