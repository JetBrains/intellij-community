package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.cache.RepositoryManager;
import com.intellij.psi.impl.source.SlaveRepositoryPsiElement;
import com.intellij.psi.impl.source.SrcRepositoryPsiElement;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.RepositoryTreeElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

/**
 *  @author dsl
 */
public class PsiTypeParameterListImpl extends SlaveRepositoryPsiElement implements PsiTypeParameterList {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiTypeParameterListImpl");

  private static final TokenSet CLASS_PARAMETER_BIT_SET = TokenSet.create(TYPE_PARAMETER);
  private static final PsiElementArrayConstructor<PsiTypeParameter> CLASS_PARAMETER_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor<PsiTypeParameter>() {
    public PsiTypeParameter[] newPsiElementArray(int length) {
      return length > 0 ? new PsiTypeParameter[length] : PsiTypeParameter.EMPTY_ARRAY;
    }
  };

  private static final TokenSet TYPE_PARAMETER_BIT_SET = TokenSet.create(JavaElementType.TYPE_PARAMETER);

  private volatile PsiTypeParameter[] myRepositoryClassParameters;
  private static final PsiFieldUpdater<PsiTypeParameterListImpl, PsiTypeParameter[]> classParametersUpdater = PsiFieldUpdater.forOnlyFieldWithType(PsiTypeParameterListImpl.class, PsiTypeParameter[].class);

  public PsiTypeParameterListImpl(PsiManagerEx manager, RepositoryTreeElement treeElement) {
    super(manager, treeElement);
  }

  public PsiTypeParameterListImpl(PsiManagerEx manager, SrcRepositoryPsiElement owner) {
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

        PsiTypeParameter[] typeParameters = count == 0 ? PsiTypeParameter.EMPTY_ARRAY : new PsiTypeParameter[count];
        for (int i = 0; i < typeParameters.length; i++) {
          typeParameters[i] = new PsiTypeParameterImpl(myManager, this, i);
        }
        // myRepositoryClassParameters = typeParameters;
        classParametersUpdater.compareAndSet(this, null, typeParameters);
      }

      return myRepositoryClassParameters;
    }
    else {
      return calcTreeElement().getChildrenAsPsiElements(CLASS_PARAMETER_BIT_SET, CLASS_PARAMETER_ARRAY_CONSTRUCTOR);
    }
  }

  public int getTypeParameterIndex(PsiTypeParameter typeParameter) {
    LOG.assertTrue(typeParameter.getParent() == this);
    return PsiImplUtil.getTypeParameterIndex(typeParameter, this);
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull PsiSubstitutor substitutor, PsiElement lastParent, @NotNull PsiElement place) {
    final PsiTypeParameter[] parameters = getTypeParameters();
    for (final PsiTypeParameter parameter : parameters) {
      if (!processor.execute(parameter, substitutor)) return false;
    }
    return true;
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitTypeParameterList(this);
  }

  public String toString() {
    return "PsiTypeParameterList";
  }
}
