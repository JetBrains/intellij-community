package com.intellij.psi.impl.source;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.SharedPsiElementImplUtil;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.util.IncorrectOperationException;

public class PsiParameterImpl extends IndexedRepositoryPsiElement implements PsiParameter {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiParameterImpl");

  private String myCachedName = null;
  private String myCachedTypeText = null;

  public PsiParameterImpl(PsiManagerImpl manager, RepositoryTreeElement treeElement) {
    super(manager, treeElement);
  }

  public PsiParameterImpl(PsiManagerImpl manager, SrcRepositoryPsiElement owner, int index) {
    super(manager, owner, index);
  }

  public void subtreeChanged() {
    super.subtreeChanged();
    myCachedName = null;
    myCachedTypeText = null;
  }

  protected Object clone() {
    PsiParameterImpl clone = (PsiParameterImpl)super.clone();
    clone.myCachedTypeText = null;
    clone.myCachedName = null;
    return clone;
  }

  public final String getName() {
    if (myCachedName == null){
      if (getTreeElement() != null){
        myCachedName = getNameIdentifier().getText();
      }
      else{
        myCachedName = getRepositoryManager().getMethodView().getParameterName(getRepositoryId(), getIndex());
      }
    }
    return myCachedName;
  }

  public PsiElement setName(String name) throws IncorrectOperationException {
    SharedPsiElementImplUtil.setName(getNameIdentifier(), name);
    return this;
  }

  public final PsiIdentifier getNameIdentifier() {
    return (PsiIdentifier)calcTreeElement().findChildByRoleAsPsiElement(ChildRole.NAME);
  }

  public PsiType getType() {
    if (getTreeElement() != null){
      return SharedImplUtil.getType(this);
    }
    else{
      myCachedTypeText = getRepositoryManager().getMethodView().getParameterTypeText(getRepositoryId(), getIndex());
      try{
        return getManager().getElementFactory().createTypeFromText(myCachedTypeText, this);
      }
      catch(IncorrectOperationException e){
        LOG.error(e);
        return null;
      }
    }
  }

  public PsiTypeElement getTypeElement() {
    return (PsiTypeElement)calcTreeElement().findChildByRoleAsPsiElement(ChildRole.TYPE);
  }

  public PsiModifierList getModifierList() {
    return (PsiModifierList)calcTreeElement().findChildByRoleAsPsiElement(ChildRole.MODIFIER_LIST);
  }

  public boolean hasModifierProperty(String name) {
    return getModifierList().hasModifierProperty(name);
  }

  public PsiExpression getInitializer() {
    return null;
  }

  public boolean hasInitializer() {
    return false;
  }

  public Object computeConstantValue() {
    return null;
  }

  public void normalizeDeclaration() throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
    SharedImplUtil.normalizeBrackets(this);
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitParameter(this);
  }

  public String toString() {
    return "PsiParameter:" + getName();
  }

  public PsiElement getDeclarationScope() {
    final PsiElement parent = getParent();
    if (parent instanceof PsiParameterList){
      return parent.getParent();
    }
    else if (parent instanceof PsiForeachStatement) {
      return parent;
    }
    else if (parent instanceof PsiCatchSection) {
      return parent.getParent();
    }
    else{
      PsiElement[] children = parent.getChildren();
      for(int i = 0; i < children.length; i++){
        if (children[i].equals(this)){
          while(!(children[i] instanceof PsiCodeBlock)){
            i++;
          }
          return children[i];
        }
      }
      LOG.assertTrue(false);
      return null;
    }
  }

  public boolean isVarArgs() {
    return TreeUtil.findChild((CompositeElement)SourceTreeToPsiMap.psiElementToTree(getTypeElement()), ELLIPSIS) != null;
  }

  public ItemPresentation getPresentation() {
    return SymbolPresentationUtil.getVariablePresentation(this);
  }
}
