package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;

public class ClsReferenceListImpl extends ClsElementImpl implements PsiReferenceList {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.compiled.ClsReferenceListImpl");

  private final PsiElement myParent;
  private PsiJavaCodeReferenceElement[] myReferences;
  private PsiClassType[] myTypes;
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

  public void setReferences(PsiJavaCodeReferenceElement[] references) {
    myReferences = references;
  }

  public PsiElement[] getChildren(){
    return myReferences;
  }

  public PsiElement getParent(){
    return myParent;
  }

  public PsiJavaCodeReferenceElement[] getReferenceElements(){
    return myReferences;
  }

  public PsiClassType[] getReferencedTypes() {
    synchronized (PsiLock.LOCK) {
      if (myTypes == null) {
        final PsiElementFactory factory = getManager().getElementFactory();
        myTypes = new PsiClassType[myReferences.length];
        for (int i = 0; i < myReferences.length; i++) {
          PsiJavaCodeReferenceElement reference = myReferences[i];
          myTypes[i] = factory.createType(reference);
        }
      }
      ;
    }
    return myTypes;
  }

  public String getMirrorText(){
    if (myReferences.length == 0) return "";
    StringBuffer buffer = new StringBuffer();
    buffer.append(myType);
    buffer.append(" ");
    for(int i = 0; i < myReferences.length; i++) {
      PsiJavaCodeReferenceElement ref = myReferences[i];
      if (i > 0) buffer.append(",");
      buffer.append(((ClsElementImpl)ref).getMirrorText());
    }
    return buffer.toString();
  }

  public void setMirror(TreeElement element){
    LOG.assertTrue(myMirror == null);
    myMirror = element;

    PsiJavaCodeReferenceElement[] refs = getReferenceElements();
    PsiJavaCodeReferenceElement[] refMirrors = ((PsiReferenceList)SourceTreeToPsiMap.treeElementToPsi(myMirror)).getReferenceElements();
    LOG.assertTrue(refs.length == refMirrors.length);
    if (refs.length == refMirrors.length){
      for(int i = 0; i < refs.length; i++) {
        ((ClsElementImpl)refs[i]).setMirror(SourceTreeToPsiMap.psiElementToTree(refMirrors[i]));
      }
    }
  }

  public void accept(PsiElementVisitor visitor){
    visitor.visitReferenceList(this);
  }

  public String toString() {
    return "PsiReferenceList";
  }


}
