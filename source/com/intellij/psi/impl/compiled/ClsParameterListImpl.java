package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;

public class ClsParameterListImpl extends ClsElementImpl implements PsiParameterList {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.compiled.ClsParameterListImpl");

  private final PsiElement myParent;
  private final ClsParameterImpl[] myParameters;

  public ClsParameterListImpl(PsiElement parent, ClsParameterImpl[] parameters) {
    myParent = parent;
    myParameters = parameters;
  }

  public PsiElement[] getChildren(){
    return myParameters;
  }

  public PsiElement getParent(){
    return myParent;
  }

  public PsiParameter[] getParameters(){
    return myParameters;
  }

  public int getParameterIndex(PsiParameter parameter) {
    LOG.assertTrue(parameter.getParent() == this);
    return PsiImplUtil.getParameterIndex(parameter, this);
  }

  public String getMirrorText(){
    StringBuffer buffer = new StringBuffer();
    buffer.append('(');
    for(int i = 0; i < myParameters.length; i++) {
      PsiParameter parm = myParameters[i];
      if (i > 0) buffer.append(",");
      buffer.append(((ClsElementImpl)parm).getMirrorText());
    }
    buffer.append(')');
    return buffer.toString();
  }

  public void setMirror(TreeElement element){
    LOG.assertTrue(myMirror == null);
    myMirror = element;

    PsiParameter[] parms = getParameters();
    PsiParameter[] parmMirrors = ((PsiParameterList)SourceTreeToPsiMap.treeElementToPsi(myMirror)).getParameters();
    LOG.assertTrue(parms.length == parmMirrors.length);
    if (parms.length == parmMirrors.length){
      for(int i = 0; i < parms.length; i++) {
        ((ClsElementImpl)parms[i]).setMirror(SourceTreeToPsiMap.psiElementToTree(parmMirrors[i]));
      }
    }
  }

  public void accept(PsiElementVisitor visitor){
    visitor.visitParameterList(this);
  }

  public String toString() {
    return "PsiParameterList";
  }
}
