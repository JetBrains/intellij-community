package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.cache.RepositoryManager;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.SlaveRepositoryPsiElement;
import com.intellij.psi.impl.source.SrcRepositoryPsiElement;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.RepositoryTreeElement;
import com.intellij.psi.scope.PsiScopeProcessor;

/**
 *  @author dsl
 */
public class PsiTypeParameterListImpl extends SlaveRepositoryPsiElement implements PsiTypeParameterList {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiTypeParameterListImpl");

  private static final TokenSet CLASS_PARAMETER_BIT_SET = TokenSet.create(new IElementType[]{TYPE_PARAMETER});
  private static final PsiElementArrayConstructor CLASS_PARAMETER_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor() {
    public PsiElement[] newPsiElementArray(int length) {
      return length > 0 ? new PsiTypeParameter[length] : PsiTypeParameter.EMPTY_ARRAY;
    }
  };

  private PsiTypeParameter[] myRepositoryClassParameters;
  private static final TokenSet TYPE_PARAMETER_BIT_SET = TokenSet.create(new IElementType[]{ElementType.TYPE_PARAMETER});

  public PsiTypeParameterListImpl(PsiManagerImpl manager, RepositoryTreeElement treeElement) {
    super(manager, treeElement);
  }

  public PsiTypeParameterListImpl(PsiManagerImpl manager, SrcRepositoryPsiElement owner) {
    super(manager, owner);
  }

  protected Object clone() {
    PsiTypeParameterListImpl clone = (PsiTypeParameterListImpl)super.clone();
    clone.myRepositoryClassParameters = null;
    return clone;
  }

  public void setOwner(SrcRepositoryPsiElement owner) {
    super.setOwner(owner);

    if (myOwner == null) {
      if (myRepositoryClassParameters != null) {
        for (int i = 0; i < myRepositoryClassParameters.length; i++) {
          PsiTypeParameterImpl ref = (PsiTypeParameterImpl)myRepositoryClassParameters[i];
          ref.setOwnerAndIndex(this, i);
        }
      }
      myRepositoryClassParameters = null;
    }
    else {
      myRepositoryClassParameters = (PsiTypeParameter[])bindIndexedSlaves(TYPE_PARAMETER_BIT_SET, CLASS_PARAMETER_ARRAY_CONSTRUCTOR);
    }
  }

  public PsiTypeParameter[] getTypeParameters() {
    long repositoryId = getRepositoryId();
    if (repositoryId >= 0) {
      if (myRepositoryClassParameters == null) {
        RepositoryManager repositoryManager = getRepositoryManager();
        CompositeElement treeElement = getTreeElement();
        int count;
        if (treeElement == null) {
          if (myOwner instanceof PsiClass) {
            count = repositoryManager.getClassView().getParametersListSize(repositoryId);
          }
          else if (myOwner instanceof PsiMethod) {
            count = repositoryManager.getMethodView().getTypeParametersCount(repositoryId);
          }
          else {
            count = 0;
            LOG.error("Wrong owner");
          }
        }
        else {
          count = treeElement.countChildren(CLASS_PARAMETER_BIT_SET);
        }

        myRepositoryClassParameters = new PsiTypeParameter[count];
        for (int i = 0; i < myRepositoryClassParameters.length; i++) {
          myRepositoryClassParameters[i] = new PsiTypeParameterImpl(myManager, this, i);
        }
      }

      return myRepositoryClassParameters;
    }
    else {
      return (PsiTypeParameter[])calcTreeElement().getChildrenAsPsiElements(CLASS_PARAMETER_BIT_SET, CLASS_PARAMETER_ARRAY_CONSTRUCTOR);
    }
  }

  public int getTypeParameterIndex(PsiTypeParameter typeParameter) {
    LOG.assertTrue(typeParameter.getParent() == this);
    return PsiImplUtil.getTypeParameterIndex(typeParameter, this);
  }

  public boolean processDeclarations(PsiScopeProcessor processor, PsiSubstitutor substitutor, PsiElement lastParent, PsiElement place) {
    final PsiTypeParameter[] parameters = getTypeParameters();
    for (int i = 0; i < parameters.length; i++) {
      final PsiTypeParameter parameter = parameters[i];
      if (!processor.execute(parameter, substitutor)) return false;
    }
    return true;
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitTypeParameterList(this);
  }

  public String toString() {
    return "PsiTypeParameterList";
  }
}
