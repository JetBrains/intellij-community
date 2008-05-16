package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypeParameterList;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiTypeParameterListStub;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class ClsTypeParametersListImpl extends ClsRepositoryPsiElement<PsiTypeParameterListStub> implements PsiTypeParameterList {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.compiled.ClsTypeParametersListImpl");

  public ClsTypeParametersListImpl(final PsiTypeParameterListStub stub) {
    super(stub);
  }

  public void appendMirrorText(final int indentLevel, final StringBuffer buffer) {
    final PsiTypeParameter[] params = getTypeParameters();
    if (params.length != 0) {
      buffer.append('<');
      for (int i = 0; i < params.length; i++) {
        ClsTypeParameterImpl parameter = (ClsTypeParameterImpl)params[i];
        if (i > 0) buffer.append(", ");
        parameter.appendMirrorText(indentLevel, buffer);
      }
      buffer.append("> ");
    }
  }

  public void setMirror(@NotNull TreeElement element) {
    LOG.assertTrue(!CHECK_MIRROR_ENABLED || myMirror == null);
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


  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitTypeParameterList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public PsiTypeParameter[] getTypeParameters() {
    return getStub().getChildrenByType(JavaStubElementTypes.TYPE_PARAMETER, PsiTypeParameter.ARRAY_FACTORY);
  }

  public int getTypeParameterIndex(PsiTypeParameter typeParameter) {
    LOG.assertTrue(typeParameter.getParent() == this);
    return PsiImplUtil.getTypeParameterIndex(typeParameter, this);
  }

  public String toString() {
    return "PsiTypeParameterList";
  }
}
