package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;

/**
 * @author max
 */
public class ClsReferenceParametersListImpl extends ClsElementImpl implements PsiReferenceParameterList {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.compiled.ClsReferenceParametersListImpl");

  private final PsiElement myParent;
  private final PsiTypeElement[] myTypeElements;

  public ClsReferenceParametersListImpl(PsiElement parent, PsiTypeElement[] typeElements) {
    myParent = parent;
    myTypeElements = typeElements;
  }

  public String getMirrorText() {
    if (myTypeElements.length == 0) return "";
    StringBuffer buf = new StringBuffer();
    buf.append('<');
    for (int i = 0; i < myTypeElements.length; i++) {
      if (i > 0) buf.append(',');
      ClsTypeElementImpl typeElement = (ClsTypeElementImpl) myTypeElements[i];
      buf.append(typeElement.getMirrorText());
    }
    buf.append('>');
    return buf.toString();
  }

  public void setMirror(TreeElement element) {
    LOG.assertTrue(myMirror == null);
    myMirror = element;

    PsiTypeElement[] typeElements = getTypeParameterElements();
    PsiTypeElement[] typeMirrors = ((PsiReferenceParameterList)SourceTreeToPsiMap.treeElementToPsi(myMirror)).getTypeParameterElements();
    LOG.assertTrue(typeElements.length == typeMirrors.length);
    if (typeElements.length == typeMirrors.length){
      for(int i = 0; i < typeElements.length; i++) {
        ((ClsElementImpl)typeElements[i]).setMirror(SourceTreeToPsiMap.psiElementToTree(typeMirrors[i]));
      }
    }
  }

  public PsiElement[] getChildren() {
    return myTypeElements;
  }

  public PsiTypeElement[] getTypeParameterElements() {
    return myTypeElements;
  }

  public PsiType[] getTypeArguments() {
    return PsiImplUtil.typesByTypeElements(myTypeElements);
  }

  public PsiElement getParent() {
    return myParent;
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitReferenceParameterList(this);
  }
}
