package com.intellij.psi.impl.source;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.RepositoryTreeElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class PsiParameterListImpl extends SlaveRepositoryPsiElement implements PsiParameterList {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiParameterListImpl");

  private volatile PsiParameter[] myRepositoryParameters = null;

  private static final PsiElementArrayConstructor PARAMETER_IMPL_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor() {
    public PsiElement[] newPsiElementArray(int length) {
      return new PsiParameterImpl[length];
    }
  };

  public PsiParameterListImpl(PsiManagerEx manager, RepositoryTreeElement treeElement) {
    super(manager, treeElement);
  }

  public PsiParameterListImpl(PsiManagerEx manager, SrcRepositoryPsiElement owner) {
    super(manager, owner);
  }

  public Object clone() {
    PsiParameterListImpl clone = (PsiParameterListImpl)super.clone();
    clone.myRepositoryParameters = null;
    return clone;
  }

  public void setOwner(SrcRepositoryPsiElement owner) {
    super.setOwner(owner);

    if (myOwner == null){
      PsiParameter[] repositoryParameters = myRepositoryParameters;
      if (repositoryParameters != null){
        for(int i = 0; i < repositoryParameters.length; i++){
          PsiParameterImpl parm = (PsiParameterImpl)repositoryParameters[i];
          parm.setOwnerAndIndex(this, i);
        }
      }
      myRepositoryParameters = null;
    }
    else{
      myRepositoryParameters = (PsiParameterImpl[])bindIndexedSlaves(PARAMETER_BIT_SET, PARAMETER_IMPL_ARRAY_CONSTRUCTOR);
    }
  }

  @NotNull
  public PsiParameter[] getParameters(){
    PsiParameter[] repositoryParameters = myRepositoryParameters;
    if (repositoryParameters != null) return repositoryParameters;
    synchronized (PsiLock.LOCK) {
      long repositoryId = getRepositoryId();
      if (repositoryId >= 0) {
        repositoryParameters = myRepositoryParameters;
        if (repositoryParameters == null) {
          int count;
          CompositeElement treeElement = getTreeElement();
          if (treeElement != null) {
            count = treeElement.countChildren(PARAMETER_BIT_SET);
          }
          else {
            count = getRepositoryManager().getMethodView().getParameterCount(repositoryId);
          }
          repositoryParameters = count == 0 ? PsiParameter.EMPTY_ARRAY : new PsiParameterImpl[count];
          for (int i = 0; i < count; i++) {
            repositoryParameters[i] = new PsiParameterImpl(myManager, this, i);
          }
          myRepositoryParameters = repositoryParameters;
        }
        return repositoryParameters;
      }
      else{
        return calcTreeElement().getChildrenAsPsiElements(PARAMETER_BIT_SET, PSI_PARAMETER_ARRAY_CONSTRUCTOR);
      }
    }
  }

  public int getParameterIndex(PsiParameter parameter) {
    LOG.assertTrue(parameter.getParent() == this);
    return PsiImplUtil.getParameterIndex(parameter, this);
  }

  public int getParametersCount() {
    long repositoryId = getRepositoryId();
    if (repositoryId >= 0) {
      PsiParameter[] repositoryParameters = myRepositoryParameters;
      if (repositoryParameters == null) {
        CompositeElement treeElement = getTreeElement();
        if (treeElement != null) {
          return treeElement.countChildren(PARAMETER_BIT_SET);
        }
        else {
          return getRepositoryManager().getMethodView().getParameterCount(repositoryId);
        }
      }
      return repositoryParameters.length;
    }
    else{
      return calcTreeElement().countChildren(PARAMETER_BIT_SET);
    }
  }

  public void accept(@NotNull PsiElementVisitor visitor){
    visitor.visitParameterList(this);
  }

  @NonNls
  public String toString(){
    return "PsiParameterList:" + getText();
  }
}
