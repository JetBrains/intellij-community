package com.intellij.psi.impl.source;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.IncorrectOperationException;

public class PsiTypeElementImpl extends CompositePsiElement implements PsiTypeElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiTypeElementImpl");
  private PsiType myCachedType = null;
  private PsiType myCachedDetachedType = null;

  public PsiTypeElementImpl() {
    super(TYPE);
  }

  public void clearCaches() {
    super.clearCaches();
    myCachedType = null;
    myCachedDetachedType = null;
  }

  public void accept(PsiElementVisitor visitor){
    visitor.visitTypeElement(this);
  }

  public String toString(){
    return "PsiTypeElement:" + getText();
  }

  public PsiType getType() {
    if (myCachedType != null) return myCachedType;

    if (firstChild.getTreeNext() == null && PRIMITIVE_TYPE_BIT_SET.isInSet(firstChild.getElementType())) {
      myCachedType = getManager().getElementFactory().createPrimitiveType(firstChild.getText());
    }
    else if (firstChild.getElementType() == ElementType.TYPE) {
      PsiType componentType = ((PsiTypeElement)SourceTreeToPsiMap.treeElementToPsi(firstChild)).getType();
      myCachedType = lastChild.getElementType() == ElementType.ELLIPSIS ? new PsiEllipsisType(componentType) : componentType.createArrayType();
    }
    else if (firstChild.getElementType() == ElementType.JAVA_CODE_REFERENCE) {
      myCachedType = new PsiClassReferenceType(getReferenceElement());
    }
    else if (firstChild.getElementType() == ElementType.QUEST) {
      PsiType temp = createWildcardType();
      myCachedType = temp;
    }

    return myCachedType;
  }

  public PsiType getDetachedType(PsiElement context) {
    if (myCachedDetachedType != null) return myCachedDetachedType;
    try {
      myCachedDetachedType = getManager().getElementFactory().createTypeFromText(getText(), context);
    }
    catch (IncorrectOperationException e) {
      return getType();
    }
    return myCachedDetachedType;
  }

  private PsiType createWildcardType() {
    final PsiType temp;
    if (firstChild.getTreeNext() == null) {
      temp = PsiWildcardType.createUnbounded(getManager());
    }
    else {
      if (lastChild.getElementType() == TYPE) {
        PsiTypeElement bound = (PsiTypeElement)SourceTreeToPsiMap.treeElementToPsi(lastChild);
        TreeElement keyword = firstChild;
        while(keyword != null && (keyword.getElementType() != EXTENDS_KEYWORD && keyword.getElementType() != SUPER_KEYWORD)) {
          keyword = keyword.getTreeNext();
        }
        if (keyword != null) {
          IElementType i = keyword.getElementType();
          if (i == EXTENDS_KEYWORD) {
            temp = PsiWildcardType.createExtends(getManager(), bound.getType());
          }
          else if (i == SUPER_KEYWORD) {
            temp = PsiWildcardType.createSuper(getManager(), bound.getType());
          }
          else {
            LOG.assertTrue(false);
            temp = PsiWildcardType.createUnbounded(getManager());
          }
        }
        else {
          temp = PsiWildcardType.createUnbounded(getManager());
        }
      } else {
        temp = PsiWildcardType.createUnbounded(getManager());
      }
    }
    return temp;
  }

  public PsiJavaCodeReferenceElement getInnermostComponentReferenceElement() {
    if (firstChild.getElementType() == ElementType.TYPE) {
      return ((PsiTypeElement)SourceTreeToPsiMap.treeElementToPsi(firstChild)).getInnermostComponentReferenceElement();
    } else {
      return getReferenceElement();
    }
  }

  private PsiJavaCodeReferenceElement getReferenceElement() {
    if (firstChild.getElementType() != ElementType.JAVA_CODE_REFERENCE) return null;
    return (PsiJavaCodeReferenceElement)SourceTreeToPsiMap.treeElementToPsi(firstChild);
  }

  public boolean processDeclarations(PsiScopeProcessor processor, PsiSubstitutor substitutor, PsiElement lastParent, PsiElement place){
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
    return true;
  }
}

