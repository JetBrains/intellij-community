package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import org.jetbrains.annotations.NotNull;

public class ClsReferenceListImpl extends ClsElementImpl implements PsiReferenceList {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.compiled.ClsReferenceListImpl");

  private final PsiElement myParent;
  private PsiJavaCodeReferenceElement[] myReferences;
  private volatile PsiClassType[] myTypes;
  private final String myType;

  public ClsReferenceListImpl(PsiElement parent, PsiJavaCodeReferenceElement[] references, String type) {
    myParent = parent;
    myReferences = references;
    LOG.assertTrue(type != null);
    myType = type;
  }

  public ClsReferenceListImpl(PsiElement parent, String type) {
    myParent = parent;
    LOG.assertTrue(type != null);
    myType = type;
  }

  public void setReferences(@NotNull PsiJavaCodeReferenceElement[] references) {
    myReferences = references;
  }

  @NotNull
  public PsiElement[] getChildren() {
    return myReferences;
  }

  public PsiElement getParent() {
    return myParent;
  }

  @NotNull
  public PsiJavaCodeReferenceElement[] getReferenceElements() {
    return myReferences;
  }

  @NotNull
  public PsiClassType[] getReferencedTypes() {
    PsiClassType[] types = myTypes;
    if (types == null) {
      final PsiElementFactory factory = getManager().getElementFactory();
      types = myReferences.length == 0 ? PsiClassType.EMPTY_ARRAY : new PsiClassType[myReferences.length];
      for (int i = 0; i < myReferences.length; i++) {
        PsiJavaCodeReferenceElement reference = myReferences[i];
        types[i] = factory.createType(reference);
      }
      myTypes = types;
    }
    return types;
  }

  public void appendMirrorText(final int indentLevel, final StringBuffer buffer) {
    if (myReferences.length != 0) {
      buffer.append(myType);
      buffer.append(" ");
      for (int i = 0; i < myReferences.length; i++) {
        PsiJavaCodeReferenceElement ref = myReferences[i];
        if (i > 0) buffer.append(", ");
        ((ClsElementImpl)ref).appendMirrorText(indentLevel, buffer);
      }
    }
  }

  public void setMirror(@NotNull TreeElement element) {
    LOG.assertTrue(myMirror == null);
    myMirror = element;

    PsiJavaCodeReferenceElement[] refs = getReferenceElements();
    PsiJavaCodeReferenceElement[] refMirrors = ((PsiReferenceList)SourceTreeToPsiMap.treeElementToPsi(element)).getReferenceElements();
    LOG.assertTrue(refs.length == refMirrors.length);
    if (refs.length == refMirrors.length) {
      for (int i = 0; i < refs.length; i++) {
          ((ClsElementImpl)refs[i]).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(refMirrors[i]));
      }
    }
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitReferenceList(this);
  }

  public String toString() {
    return "PsiReferenceList";
  }


}
