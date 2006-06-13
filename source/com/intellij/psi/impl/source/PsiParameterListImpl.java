package com.intellij.psi.impl.source;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.RepositoryTreeElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

public class PsiParameterListImpl extends SlaveRepositoryPsiElement implements PsiParameterList {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiParameterListImpl");

  private PsiParameterImpl[] myRepositoryParameters = null;

  private static final PsiElementArrayConstructor PARAMETER_IMPL_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor() {
    public PsiElement[] newPsiElementArray(int length) {
      return new PsiParameterImpl[length];
    }
  };

  public PsiParameterListImpl(PsiManagerImpl manager, RepositoryTreeElement treeElement) {
    super(manager, treeElement);
  }

  public PsiParameterListImpl(PsiManagerImpl manager, SrcRepositoryPsiElement owner) {
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
      if (myRepositoryParameters != null){
        for(int i = 0; i < myRepositoryParameters.length; i++){
          PsiParameterImpl parm = myRepositoryParameters[i];
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
    long repositoryId = getRepositoryId();
    if (repositoryId >= 0) {
      if (myRepositoryParameters == null) {
        int count;
        CompositeElement treeElement = getTreeElement();
        if (treeElement != null) {
          count = treeElement.countChildren(PARAMETER_BIT_SET);
        }
        else {
          count = getRepositoryManager().getMethodView().getParameterCount(repositoryId);
        }
        PsiParameterImpl[] temp = new PsiParameterImpl[count];
        for (int i = 0; i < count; i++) {
          temp[i] = new PsiParameterImpl(myManager, this, i);
        }
        myRepositoryParameters = temp;
      }
      return myRepositoryParameters;
    }
    else{
      return calcTreeElement().getChildrenAsPsiElements(PARAMETER_BIT_SET, PSI_PARAMETER_ARRAY_CONSTRUCTOR);
    }
  }

  public int getParameterIndex(PsiParameter parameter) {
    LOG.assertTrue(parameter.getParent() == this);
    return PsiImplUtil.getParameterIndex(parameter, this);
  }

  public int getParametersCount() {
    long repositoryId = getRepositoryId();
    if (repositoryId >= 0) {
      if (myRepositoryParameters == null) {
        CompositeElement treeElement = getTreeElement();
        if (treeElement != null) {
          return treeElement.countChildren(PARAMETER_BIT_SET);
        }
        else {
          return getRepositoryManager().getMethodView().getParameterCount(repositoryId);
        }

      }
      return myRepositoryParameters.length;
    }
    else{
      return calcTreeElement().countChildren(PARAMETER_BIT_SET);
    }
  }

  public void accept(PsiElementVisitor visitor){
    visitor.visitParameterList(this);
  }

  @NonNls
  public String toString(){
    return "PsiParameterList:" + getText();
  }
}
