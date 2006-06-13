package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

public class ClsParameterListImpl extends ClsElementImpl implements PsiParameterList {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.compiled.ClsParameterListImpl");

  private final PsiElement myParent;
  private final ClsParameterImpl[] myParameters;

  public ClsParameterListImpl(PsiElement parent, ClsParameterImpl[] parameters) {
    myParent = parent;
    myParameters = parameters;
  }

  @NotNull
  public PsiElement[] getChildren() {
    return myParameters;
  }

  public PsiElement getParent() {
    return myParent;
  }

  @NotNull
  public PsiParameter[] getParameters() {
    return myParameters;
  }

  public int getParameterIndex(PsiParameter parameter) {
    LOG.assertTrue(parameter.getParent() == this);
    return PsiImplUtil.getParameterIndex(parameter, this);
  }

  public int getParametersCount() {
    return myParameters.length;
  }

  public void appendMirrorText(final int indentLevel, final StringBuffer buffer) {
    buffer.append('(');
    for (int i = 0; i < myParameters.length; i++) {
      PsiParameter parm = myParameters[i];
      if (i > 0) buffer.append(", ");
      ((ClsElementImpl)parm).appendMirrorText(indentLevel, buffer);
    }
    buffer.append(')');
  }

  public void setMirror(TreeElement element) {
    LOG.assertTrue(myMirror == null);
    myMirror = element;

    PsiParameter[] parms = getParameters();
    PsiParameter[] parmMirrors = ((PsiParameterList)SourceTreeToPsiMap.treeElementToPsi(myMirror)).getParameters();
    LOG.assertTrue(parms.length == parmMirrors.length);
    if (parms.length == parmMirrors.length) {
      for (int i = 0; i < parms.length; i++) {
          ((ClsElementImpl)parms[i]).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(parmMirrors[i]));
      }
    }
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitParameterList(this);
  }

  @NonNls
  public String toString() {
    return "PsiParameterList";
  }
}
