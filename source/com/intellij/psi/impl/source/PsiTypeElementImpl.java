package com.intellij.psi.impl.source;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.lang.ASTNode;

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

    if (getFirstChildNode().getTreeNext() == null && PRIMITIVE_TYPE_BIT_SET.isInSet(getFirstChildNode().getElementType())) {
      myCachedType = getManager().getElementFactory().createPrimitiveType(getFirstChildNode().getText());
    }
    else if (getFirstChildNode().getElementType() == ElementType.TYPE) {
      PsiType componentType = ((PsiTypeElement)SourceTreeToPsiMap.treeElementToPsi(getFirstChildNode())).getType();
      myCachedType = getLastChildNode().getElementType() == ElementType.ELLIPSIS ? new PsiEllipsisType(componentType) : componentType.createArrayType();
    }
    else if (getFirstChildNode().getElementType() == ElementType.JAVA_CODE_REFERENCE) {
      myCachedType = new PsiClassReferenceType(getReferenceElement());
    }
    else if (getFirstChildNode().getElementType() == ElementType.QUEST) {
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
    if (getFirstChildNode().getTreeNext() == null) {
      temp = PsiWildcardType.createUnbounded(getManager());
    }
    else {
      if (getLastChildNode().getElementType() == TYPE) {
        PsiTypeElement bound = (PsiTypeElement)SourceTreeToPsiMap.treeElementToPsi(getLastChildNode());
        ASTNode keyword = getFirstChildNode();
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
    if (getFirstChildNode().getElementType() == ElementType.TYPE) {
      return ((PsiTypeElement)SourceTreeToPsiMap.treeElementToPsi(getFirstChildNode())).getInnermostComponentReferenceElement();
    } else {
      return getReferenceElement();
    }
  }

  private PsiJavaCodeReferenceElement getReferenceElement() {
    if (getFirstChildNode().getElementType() != ElementType.JAVA_CODE_REFERENCE) return null;
    return (PsiJavaCodeReferenceElement)SourceTreeToPsiMap.treeElementToPsi(getFirstChildNode());
  }

  public boolean processDeclarations(PsiScopeProcessor processor, PsiSubstitutor substitutor, PsiElement lastParent, PsiElement place){
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
    return true;
  }
}

