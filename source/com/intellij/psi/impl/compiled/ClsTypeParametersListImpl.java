package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypeParameterList;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.PsiImplUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class ClsTypeParametersListImpl extends ClsElementImpl implements PsiTypeParameterList {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.compiled.ClsTypeParametersListImpl");

  private final PsiElement myParent;
  private ClsTypeParameterImpl[] myParameters;

  public ClsTypeParametersListImpl(PsiElement parent, ClsTypeParameterImpl[] parameters) {
    myParent = parent;
    myParameters = parameters;
  }

  public ClsTypeParametersListImpl(PsiElement parent) {
    myParent = parent;
  }

  public void setParameters(ClsTypeParameterImpl[] parameters) {
    myParameters = parameters;
  }

  public void appendMirrorText(final int indentLevel, final StringBuffer buffer) {
    if (myParameters.length != 0) {
      buffer.append('<');
      for (int i = 0; i < myParameters.length; i++) {
        ClsTypeParameterImpl parameter = myParameters[i];
        if (i > 0) buffer.append(", ");
        parameter.appendMirrorText(indentLevel, buffer);
      }
      buffer.append("> ");
    }
  }

  public void setMirror(TreeElement element) {
    LOG.assertTrue(myMirror == null);
    myMirror = element;

    PsiTypeParameter[] parms = getTypeParameters();
    PsiTypeParameter[] parmMirrors = ((PsiTypeParameterList)SourceTreeToPsiMap.treeElementToPsi(myMirror)).getTypeParameters();
    LOG.assertTrue(parms.length == parmMirrors.length);
    if (parms.length == parmMirrors.length) {
      for (int i = 0; i < parms.length; i++) {
          ((ClsElementImpl)parms[i]).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(parmMirrors[i]));
      }
    }
  }

  @NotNull
  public PsiElement[] getChildren() {
    return myParameters;
  }

  public PsiElement getParent() {
    return myParent;
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitTypeParameterList(this);
  }

  public PsiTypeParameter[] getTypeParameters() {
    return myParameters;
  }

  public int getTypeParameterIndex(PsiTypeParameter typeParameter) {
    LOG.assertTrue(typeParameter.getParent() == this);
    return PsiImplUtil.getTypeParameterIndex(typeParameter, this);
  }

  public String toString() {
    return "PsiTypeParameterList";
  }
}
