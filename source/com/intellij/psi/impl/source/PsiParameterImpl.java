package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.cache.RecordUtil;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiParameterStub;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.JavaSharedImplUtil;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.presentation.java.JavaPresentationUtil;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.ui.RowIcon;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PatchedSoftReference;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class PsiParameterImpl extends JavaStubPsiElement<PsiParameterStub> implements PsiParameter {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiParameterImpl");
  private PatchedSoftReference<PsiType> myCachedType = null;

  public PsiParameterImpl(final PsiParameterStub stub) {
    super(stub, JavaStubElementTypes.PARAMETER);
  }

  public PsiParameterImpl(final ASTNode node) {
    super(node);
  }

  public void subtreeChanged() {
    super.subtreeChanged();
    myCachedType = null;
  }

  protected Object clone() {
    PsiParameterImpl clone = (PsiParameterImpl)super.clone();
    clone.myCachedType = null;

    return clone;
  }

  public final String getName() {
    final PsiParameterStub stub = getStub();
    if (stub != null) {
      return stub.getName();
    }
    return getNameIdentifier().getText();
  }

  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    PsiImplUtil.setName(getNameIdentifier(), name);
    return this;
  }

  @NotNull
  public final PsiIdentifier getNameIdentifier() {
    return (PsiIdentifier)getNode().findChildByRoleAsPsiElement(ChildRole.NAME);
  }

  @NotNull
  public CompositeElement getNode() {
    return (CompositeElement)super.getNode();
  }

  @NotNull
  public PsiType getType() {
    final PsiParameterStub stub = getStub();
    if (stub != null) {
      if (myCachedType != null) {
        PsiType type = myCachedType.get();
        if (type != null) return type;
      }

      String typeText = RecordUtil.createTypeText(stub.getParameterType());
      try {
        final PsiType type = JavaPsiFacade.getInstance(getProject()).getParserFacade().createTypeFromText(typeText, this);
        myCachedType = new PatchedSoftReference<PsiType>(type);
        return type;
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
        return null;
      }
    }

    myCachedType = null;
    return JavaSharedImplUtil.getType(this);
  }

  @NotNull
  public PsiTypeElement getTypeElement() {
    return (PsiTypeElement)getNode().findChildByRoleAsPsiElement(ChildRole.TYPE);
  }

  public PsiModifierList getModifierList() {
    return getStubOrPsiChild(JavaStubElementTypes.MODIFIER_LIST);
  }

  public boolean hasModifierProperty(@NotNull String name) {
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
    JavaSharedImplUtil.normalizeBrackets(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitParameter(this);
    }
    else {
      visitor.visitElement(this);
    }
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
    final PsiParameterStub stub = getStub();
    if (stub != null) {
      return stub.isParameterTypeEllipsis();
    }

    myCachedType = null;
    return TreeUtil.findChild(SourceTreeToPsiMap.psiElementToTree(getTypeElement()), Constants.ELLIPSIS) != null;
  }

  @NotNull
  public PsiAnnotation[] getAnnotations() {
    return getModifierList().getAnnotations();
  }

  public ItemPresentation getPresentation() {
    return JavaPresentationUtil.getVariablePresentation(this);
  }

  public Icon getElementIcon(final int flags) {
    final RowIcon baseIcon = createLayeredIcon(Icons.PARAMETER_ICON, 0);
    return ElementPresentationUtil.addVisibilityIcon(this, flags, baseIcon);
  }

  @NotNull
  public SearchScope getUseScope() {
    return new LocalSearchScope(getDeclarationScope());
  }
}