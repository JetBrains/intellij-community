package com.intellij.psi.impl.source;

import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.RepositoryTreeElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;

public class PsiClassInitializerImpl extends NonSlaveRepositoryPsiElement implements PsiClassInitializer {
  private PsiModifierListImpl myRepositoryModifierList = null;

  public PsiClassInitializerImpl(PsiManagerImpl manager, long repositoryId) {
    super(manager, repositoryId);
  }

  public PsiClassInitializerImpl(PsiManagerImpl manager, RepositoryTreeElement treeElement) {
    super(manager, treeElement);
  }

  protected Object clone() {
    PsiClassInitializerImpl clone = (PsiClassInitializerImpl)super.clone();
    clone.myRepositoryModifierList = null;
    return clone;
  }

  public void setRepositoryId(long repositoryId) {
    super.setRepositoryId(repositoryId);

    if (repositoryId < 0){
      if (myRepositoryModifierList != null){
        myRepositoryModifierList.setOwner(this);
        myRepositoryModifierList = null;
      }
    }
    else{
      myRepositoryModifierList = (PsiModifierListImpl)bindSlave(ChildRole.MODIFIER_LIST);
    }
  }

  public PsiClass getContainingClass() {
    PsiElement parent = getParent();
    return parent instanceof PsiClass ? (PsiClass)parent : null;
  }

  public PsiModifierList getModifierList(){
    long repositoryId = getRepositoryId();
    if (repositoryId >= 0){
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

  public PsiCodeBlock getBody(){
    return (PsiCodeBlock)calcTreeElement().findChildByRoleAsPsiElement(ChildRole.METHOD_BODY);
  }

  public void accept(PsiElementVisitor visitor){
    visitor.visitClassInitializer(this);
  }

  public String toString(){
    return "PsiClassInitializer";
  }

  public boolean processDeclarations(PsiScopeProcessor processor, PsiSubstitutor substitutor, PsiElement lastParent, PsiElement place) {
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
    if(lastParent == null) return true;
    return PsiScopesUtil.walkChildrenScopes(this, processor, substitutor, lastParent, place);
  }
}

