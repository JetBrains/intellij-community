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
import com.intellij.util.PatchedSoftReference;
import org.jetbrains.annotations.NotNull;

public class PsiParameterImpl extends IndexedRepositoryPsiElement implements PsiParameter {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiParameterImpl");

  private String myCachedName = null;
  private PatchedSoftReference<PsiType> myCachedType = null;
  private Boolean myCachedIsVarArgs = null;
  private PsiAnnotation[] myCachedAnnotations = null;

  public PsiParameterImpl(PsiManagerImpl manager, RepositoryTreeElement treeElement) {
    super(manager, treeElement);
  }

  public PsiParameterImpl(PsiManagerImpl manager, SrcRepositoryPsiElement owner, int index) {
    super(manager, owner, index);
  }

  public void subtreeChanged() {
    super.subtreeChanged();
    myCachedName = null;
    myCachedType = null;
    myCachedIsVarArgs = null;
    myCachedAnnotations = null;
  }

  protected Object clone() {
    PsiParameterImpl clone = (PsiParameterImpl)super.clone();
    clone.myCachedType = null;
    clone.myCachedName = null;
    clone.myCachedIsVarArgs = null;
    clone.myCachedAnnotations = null;

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

  @NotNull
  public final PsiIdentifier getNameIdentifier() {
    return (PsiIdentifier)calcTreeElement().findChildByRoleAsPsiElement(ChildRole.NAME);
  }

  @NotNull
  public PsiType getType() {
    final CompositeElement treeElement = getTreeElement();
    if (treeElement != null) {
      myCachedType = null;
      return SharedImplUtil.getType(this);
    }
    else {
      if (myCachedType != null) {
        PsiType type = myCachedType.get();
        if (type != null) return type;
      }

      String typeText = getRepositoryManager().getMethodView().getParameterTypeText(getRepositoryId(), getIndex());
      try {
        final PsiType type = getManager().getParserFacade().createTypeFromText(typeText, this);
        myCachedType = new PatchedSoftReference<PsiType>(type);
        return type;
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
        return null;
      }
    }
  }

  @NotNull
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

  @NotNull
  public PsiElement getDeclarationScope() {
    final PsiElement parent = getParent();
    if (parent instanceof PsiParameterList){
      return parent.getParent();
    }
    else if (parent instanceof PsiForeachStatement) {
      return parent;
    }
    else if (parent instanceof PsiCatchSection) {
      return parent;
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
    final CompositeElement treeElement = getTreeElement();
    if (treeElement != null) {
      myCachedType = null;
      return TreeUtil.findChild(SourceTreeToPsiMap.psiElementToTree(getTypeElement()), ELLIPSIS) != null;
    }
    else {
      if (myCachedIsVarArgs == null) {
        myCachedIsVarArgs = getRepositoryManager().getMethodView().isParameterTypeEllipsis(getRepositoryId(), getIndex());
      }
      return myCachedIsVarArgs.booleanValue();
    }
  }

  @NotNull
  public PsiAnnotation[] getAnnotations() {
    final CompositeElement treeElement = getTreeElement();
    if (treeElement != null) {
      myCachedAnnotations = null;
      return getModifierList().getAnnotations();
    }
    else {
      if (myCachedAnnotations == null) {
        String[] annotationStrings = getRepositoryManager().getMethodView().getParameterAnnotations(getRepositoryId())[getIndex()];
        PsiAnnotation[] temp = new PsiAnnotation[annotationStrings.length];
        for (int i = 0; i < annotationStrings.length; i++) {
          try {
            temp[i] = getManager().getParserFacade().createAnnotationFromText(annotationStrings[i], this);
            LOG.assertTrue(temp[i] != null);
          }
          catch (IncorrectOperationException e) {
            LOG.error("Bad annotation text in repository: " + annotationStrings[i]);
          }
        }
        myCachedAnnotations = temp;
      }
      return myCachedAnnotations;
    }
  }

  public ItemPresentation getPresentation() {
    return SymbolPresentationUtil.getVariablePresentation(this);
  }
}
