package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiParameterListStub;
import com.intellij.psi.impl.source.tree.CompositeElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class PsiParameterListImpl extends JavaStubPsiElement<PsiParameterListStub> implements PsiParameterList {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiParameterListImpl");

  public PsiParameterListImpl(final PsiParameterListStub stub) {
    super(stub, JavaStubElementTypes.PARAMETER_LIST);
  }

  public PsiParameterListImpl(final ASTNode node) {
    super(node);
  }

  @NotNull
  public PsiParameter[] getParameters() {
    return getStubOrPsiChildren(JavaStubElementTypes.PARAMETER, PsiParameter.ARRAY_FACTORY);
  }

  public int getParameterIndex(PsiParameter parameter) {
    LOG.assertTrue(parameter.getParent() == this);
    return PsiImplUtil.getParameterIndex(parameter, this);
  }

  @NotNull
  public CompositeElement getNode() {
    return (CompositeElement)super.getNode();
  }

  public int getParametersCount() {
    final PsiParameterListStub stub = getStub();
    if (stub != null) {
      return stub.getChildrenStubs().size();
    }

    return getNode().countChildren(Constants.PARAMETER_BIT_SET);
  }

  public void accept(@NotNull PsiElementVisitor visitor){
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitParameterList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @NonNls
  public String toString(){
    return "PsiParameterList:" + getText();
  }
}