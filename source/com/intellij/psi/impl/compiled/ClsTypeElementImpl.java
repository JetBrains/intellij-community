package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ClsTypeElementImpl extends ClsElementImpl implements PsiTypeElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.compiled.ClsTypeImpl");
  static final char VARIANCE_NONE = '\0';
  static final char VARIANCE_EXTENDS = '+';
  static final char VARIANCE_SUPER = '-';
  static final char VARIANCE_INVARIANT = '*';


  private PsiElement myParent;
  private final String myTypeText;

  private ClsElementImpl myChild = null;
  private boolean myChildSet = false;
  private PsiType myCachedType;
  private char myVariance;
  private static final @NonNls String VARIANCE_EXTENDS_PREFIX = "? extends ";
  private static final @NonNls String VARIANCE_SUPER_PREFIX = "? super ";

  public ClsTypeElementImpl(PsiElement parent, String typeText, char variance) {
    myParent = parent;
    myTypeText = typeText;
    myVariance = variance;
  }

  void setParent(PsiElement parent){
    myParent = parent;
  }

  @NotNull
  public PsiElement[] getChildren(){
    loadChild();
    if (myChild == null) return PsiElement.EMPTY_ARRAY;
    return new PsiElement[] {myChild};
  }

  public PsiElement getParent(){
    return myParent;
  }

  public String getText(){
    final String shortClassName = PsiNameHelper.getShortClassName(myTypeText);
    return decorateTypeText(shortClassName);
  }

  private String decorateTypeText(final String shortClassName) {
    switch(myVariance) {
      case VARIANCE_NONE:
        return shortClassName;
      case VARIANCE_EXTENDS:
        return VARIANCE_EXTENDS_PREFIX + shortClassName;
      case VARIANCE_SUPER:
        return VARIANCE_SUPER_PREFIX + shortClassName;
      case VARIANCE_INVARIANT:
        return "?";
      default:
        LOG.assertTrue(false);
        return null;
    }
  }

  public String getCanonicalText(){
    return decorateTypeText(myTypeText);
  }

  public void appendMirrorText(final int indentLevel, final StringBuffer buffer){
    buffer.append(decorateTypeText(myTypeText));
  }

  public void setMirror(TreeElement element){
    LOG.assertTrue(myMirror == null);
    LOG.assertTrue(element.getElementType() == ElementType.TYPE);
    myMirror = element;

    loadChild();

    if (myChild != null) {
      myChild.setMirror((TreeElement)element.getFirstChildNode());
    }
  }

  private void loadChild() {
    if (isPrimitive()) {
      myChildSet = true;
      return;
    }

    if (isArray() || isVarArgs()) {
      createComponentTypeChild();
    }
    else {
      createClassReferenceChild();
    }
  }

  private boolean isPrimitive() {
    return getManager().getElementFactory().createPrimitiveType(myTypeText) != null;
  }

  private boolean isArray() {
    return myTypeText.endsWith("[]");
  }

  private boolean isVarArgs() {
    return myTypeText.endsWith("...");
  }

  @NotNull
  public PsiType getType() {
    if (myCachedType == null) {
      myCachedType = calculateType();
    }
    return myCachedType;
  }

  public PsiJavaCodeReferenceElement getInnermostComponentReferenceElement() {
    return null;
  }

  private PsiType calculateType() {
    PsiType result = getManager().getElementFactory().createPrimitiveType(myTypeText);
    if (result != null) return result;

    if (isArray()) {
      createComponentTypeChild();
      if (myVariance == VARIANCE_NONE) return ((PsiTypeElement)myChild).getType().createArrayType();
      switch(myVariance) {
        case VARIANCE_EXTENDS:
          return PsiWildcardType.createExtends(getManager(), ((PsiTypeElement) myChild).getType());
        case VARIANCE_SUPER:
          return PsiWildcardType.createSuper(getManager(), ((PsiTypeElement) myChild).getType());
        default:
          LOG.assertTrue(false);
          return null;
      }
    } else if (isVarArgs()) {
      createComponentTypeChild();
      return new PsiEllipsisType(((PsiTypeElement)myChild).getType());
    }

    createClassReferenceChild();
    final PsiClassReferenceType psiClassReferenceType;
    if (myVariance != VARIANCE_INVARIANT) {
      psiClassReferenceType = new PsiClassReferenceType((PsiJavaCodeReferenceElement) myChild);
    }
    else {
      psiClassReferenceType = null;
    }

    switch(myVariance) {
      case VARIANCE_NONE:
        return psiClassReferenceType;
      case VARIANCE_EXTENDS:
        return PsiWildcardType.createExtends(getManager(), psiClassReferenceType);
      case VARIANCE_SUPER:
        return PsiWildcardType.createSuper(getManager(), psiClassReferenceType);
      case VARIANCE_INVARIANT:
        return PsiWildcardType.createUnbounded(getManager());
      default:
        LOG.assertTrue(false);
        return null;
    }
  }

  private void createClassReferenceChild() {
    synchronized (PsiLock.LOCK) {
      if (!myChildSet) {
        if (myVariance != VARIANCE_INVARIANT) {
          myChild = new ClsJavaCodeReferenceElementImpl(this, myTypeText);
        }
        myChildSet = true;
      }
    }
  }

  private void createComponentTypeChild() {
    synchronized (PsiLock.LOCK) {
      if (!myChildSet) {
        if (isArray()) {
          if (myVariance == VARIANCE_NONE) {
            myChild = new ClsTypeElementImpl(this, myTypeText.substring(0, myTypeText.length() - 2), myVariance);
          }
          else {
            myChild = new ClsTypeElementImpl(this, myTypeText, VARIANCE_NONE);
          }
        }
        else if (isVarArgs()) {
          myChild = new ClsTypeElementImpl(this, myTypeText.substring(0, myTypeText.length() - 3), myVariance);
        }
        else {
          LOG.assertTrue(false);
        }
        myChildSet = true;
      }
    }
  }

  public void accept(PsiElementVisitor visitor){
    visitor.visitTypeElement(this);
  }

  public String toString() {
    return "PsiTypeElement:" + getText();
  }

}
