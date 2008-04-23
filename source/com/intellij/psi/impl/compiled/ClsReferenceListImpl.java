package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.PsiClassReferenceListStub;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings({"HardCodedStringLiteral"})
public class ClsReferenceListImpl extends ClsRepositoryPsiElement<PsiClassReferenceListStub> implements PsiReferenceList {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.compiled.ClsReferenceListImpl");
  private ClsJavaCodeReferenceElementImpl[] myRefs;

  public ClsReferenceListImpl(final PsiClassReferenceListStub stub) {
    super(stub);
  }

  @NotNull
  public PsiJavaCodeReferenceElement[] getReferenceElements() {
    synchronized (PsiLock.LOCK) {
      if (myRefs == null) {
        final String[] strings = getStub().getReferencedNames();
        ClsJavaCodeReferenceElementImpl[] res = new ClsJavaCodeReferenceElementImpl[strings.length];
        for (int i = 0; i < res.length; i++) {
            res[i] = new ClsJavaCodeReferenceElementImpl(this, strings[i]);
          }
        myRefs = res;
      }
      return myRefs;
    }
  }

  @NotNull
  public PsiElement[] getChildren() {
    return getReferenceElements();
  }

  @NotNull
  public PsiClassType[] getReferencedTypes() {
    return getStub().getReferencedTypes();
  }

  public Role getRole() {
    return getStub().getRole();
  }

  public void appendMirrorText(final int indentLevel, final StringBuffer buffer) {
    final String[] names = getStub().getReferencedNames();
    if (names.length != 0) {
      final Role role = getStub().getRole();
      switch (role) {
        case EXTENDS_BOUNDS_LIST:
        case EXTENDS_LIST:
          buffer.append("extends ");
          break;
        case IMPLEMENTS_LIST:
          buffer.append("implements ");
          break;
        case THROWS_LIST:
          buffer.append("throws ");
          break;
      }
      for (int i = 0; i < names.length; i++) {
        if (i > 0) buffer.append(", ");
        buffer.append(names[i]);
      }
    }
  }

  public void setMirror(@NotNull TreeElement element) {
    LOG.assertTrue(!CHECK_MIRROR_ENABLED || myMirror == null);
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
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitReferenceList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiReferenceList";
  }


}
