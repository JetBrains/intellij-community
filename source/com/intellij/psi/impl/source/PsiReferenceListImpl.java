package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.PsiClassReferenceListStub;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

public final class PsiReferenceListImpl extends JavaStubPsiElement<PsiClassReferenceListStub> implements PsiReferenceList {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiReferenceListImpl");
  private static final TokenSet REFERENCE_BIT_SET = TokenSet.create(Constants.JAVA_CODE_REFERENCE);

  public PsiReferenceListImpl(final PsiClassReferenceListStub stub, final IStubElementType nodeType) {
    super(stub, nodeType);
  }

  public PsiReferenceListImpl(final ASTNode node) {
    super(node);
  }

  @NotNull
  public PsiJavaCodeReferenceElement[] getReferenceElements() {
    return calcTreeElement().getChildrenAsPsiElements(REFERENCE_BIT_SET, Constants.PSI_REFERENCE_ELEMENT_ARRAY_CONSTRUCTOR);
  }

  @NotNull
  public PsiClassType[] getReferencedTypes() {
    final PsiClassReferenceListStub stub = getStub();
    if (stub != null) {
      return stub.getReferencedTypes();
    }

    final PsiJavaCodeReferenceElement[] refs = getReferenceElements();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(getManager().getProject()).getElementFactory();
    PsiClassType[] types = new PsiClassType[refs.length];
    for (int i = 0; i < types.length; i++) {
      types[i] = factory.createType(refs[i]);
    }

    return types;
  }

  public Role getRole() {
    final IElementType tt = getElementType();

    if (tt == JavaElementType.EXTENDS_LIST) {
      return Role.EXTENDS_LIST;
    }
    else if (tt == JavaElementType.IMPLEMENTS_LIST) {
      return Role.IMPLEMENTS_LIST;
    }
    else if (tt == JavaElementType.THROWS_LIST) {
      return Role.THROWS_LIST;
    }
    else if (tt == JavaElementType.EXTENDS_BOUND_LIST) {
      return Role.EXTENDS_BOUNDS_LIST;
    }
    else {
      LOG.error("Unknown element type:" + tt);
      return null;
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
