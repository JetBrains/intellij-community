package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.*;

public class PsiNewExpressionImpl extends CompositePsiElement implements PsiNewExpression {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiNewExpressionImpl");

  public PsiNewExpressionImpl() {
    super(NEW_EXPRESSION);
  }

  public PsiType getType(){
    PsiType type = null;
    for(TreeElement child = firstChild; child != null; child = child.getTreeNext()){
      if (child.getElementType() == JAVA_CODE_REFERENCE){
        LOG.assertTrue(type == null);
        type = new PsiClassReferenceType((PsiJavaCodeReferenceElement)SourceTreeToPsiMap.treeElementToPsi(child));
      }
      else if (PRIMITIVE_TYPE_BIT_SET.isInSet(child.getElementType())){
        LOG.assertTrue(type == null);
        type = getManager().getElementFactory().createPrimitiveType(child.getText());
      }
      else if (child.getElementType() == LBRACKET){
        LOG.assertTrue(type != null);
        type = type.createArrayType();
      }
      else if (child.getElementType() == ANONYMOUS_CLASS){
        PsiElementFactory factory = getManager().getElementFactory();
        type = factory.createType((PsiClass)SourceTreeToPsiMap.treeElementToPsi(child));
      }
    }
    return type;
  }

  public PsiExpressionList getArgumentList() {
    PsiExpressionList list = (PsiExpressionList)findChildByRoleAsPsiElement(ChildRole.ARGUMENT_LIST);
    if (list != null) return list;
    CompositeElement anonymousClass = (CompositeElement)SourceTreeToPsiMap.psiElementToTree(findChildByRoleAsPsiElement(ChildRole.ANONYMOUS_CLASS));
    if (anonymousClass != null){
      return (PsiExpressionList)anonymousClass.findChildByRoleAsPsiElement(ChildRole.ARGUMENT_LIST);
    }
    return null;
  }

  public PsiExpression[] getArrayDimensions() {
    PsiExpression[] expressions = (PsiExpression[])getChildrenAsPsiElements(ARRAY_DIMENSION_BIT_SET, PSI_EXPRESSION_ARRAY_CONSTRUCTOR);
    PsiExpression qualifier = getQualifier();
    if (qualifier == null){
      return expressions;
    }
    else{
      LOG.assertTrue(expressions[0] == qualifier);
      PsiExpression[] expressions1 = new PsiExpression[expressions.length - 1];
      System.arraycopy(expressions, 1, expressions1, 0, expressions1.length);
      return expressions1;
    }
  }

  public PsiArrayInitializerExpression getArrayInitializer() {
    return (PsiArrayInitializerExpression)findChildByRoleAsPsiElement(ChildRole.ARRAY_INITIALIZER);
  }

  public PsiMethod resolveMethod() {
    return resolveConstructor();
  }

  public ResolveResult resolveMethodGenerics() {
    TreeElement classRef = findChildByRole(ChildRole.TYPE_REFERENCE);
    if (classRef != null){
      TreeElement argumentList = TreeUtil.skipElements(classRef.getTreeNext(), WHITE_SPACE_OR_COMMENT_BIT_SET);
      if (argumentList != null && argumentList.getElementType() == EXPRESSION_LIST) {
        PsiType aClass = getManager().getElementFactory().createType(
          (PsiJavaCodeReferenceElement)SourceTreeToPsiMap.treeElementToPsi(classRef));
        if (aClass != null) {
          return getManager().getResolveHelper().resolveConstructor((PsiClassType)aClass,
                                                                    (PsiExpressionList)SourceTreeToPsiMap.treeElementToPsi(argumentList),
                                                                    this);
        }
      }
    }
    else{
      TreeElement anonymousClass = TreeUtil.findChild(this, ANONYMOUS_CLASS);
      if (anonymousClass != null) {
        PsiType aClass = ((PsiAnonymousClass)SourceTreeToPsiMap.treeElementToPsi(anonymousClass)).getBaseClassType();
        if (aClass != null) {
          TreeElement argumentList = TreeUtil.findChild((CompositeElement)anonymousClass, EXPRESSION_LIST);
          return getManager().getResolveHelper().resolveConstructor((PsiClassType)aClass,
                                                                    (PsiExpressionList)SourceTreeToPsiMap.treeElementToPsi(argumentList),
                                                                    this);
        }
      }
    }

    return ResolveResult.EMPTY;
  }

  public PsiExpression getQualifier() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.QUALIFIER);
  }

  public PsiReferenceParameterList getTypeArgumentList() {
    return (PsiReferenceParameterList) findChildByRoleAsPsiElement(ChildRole.REFERENCE_PARAMETER_LIST);
  }

  public PsiType[] getTypeArguments() {
    return getTypeArgumentList().getTypeArguments();
  }

  public PsiMethod resolveConstructor(){
    return (PsiMethod)resolveMethodGenerics().getElement();
  }

  public PsiJavaCodeReferenceElement getClassReference() {
    return (PsiJavaCodeReferenceElement)findChildByRoleAsPsiElement(ChildRole.TYPE_REFERENCE);
  }

  public PsiAnonymousClass getAnonymousClass() {
    TreeElement anonymousClass = TreeUtil.findChild(this, ANONYMOUS_CLASS);
    if (anonymousClass == null) return null;
    return (PsiAnonymousClass)SourceTreeToPsiMap.treeElementToPsi(anonymousClass);
  }

  public void deleteChildInternal(TreeElement child) {
    if (getChildRole(child) == ChildRole.QUALIFIER){
      TreeElement dot = findChildByRole(ChildRole.DOT);
      super.deleteChildInternal(child);
      deleteChildInternal(dot);
    }
    else{
      super.deleteChildInternal(child);
    }
  }

  public TreeElement findChildByRole(int role){
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.REFERENCE_PARAMETER_LIST:
        return TreeUtil.findChild(this, REFERENCE_PARAMETER_LIST);

      case ChildRole.QUALIFIER:
        return firstChild.getElementType() != NEW_KEYWORD ? firstChild : null;

      case ChildRole.DOT:
        return TreeUtil.findChild(this, DOT);

      case ChildRole.NEW_KEYWORD:
        return TreeUtil.findChild(this, NEW_KEYWORD);

      case ChildRole.ANONYMOUS_CLASS:
        return TreeUtil.findChild(this, ANONYMOUS_CLASS);

      case ChildRole.TYPE_REFERENCE:
        return TreeUtil.findChild(this, JAVA_CODE_REFERENCE);

      case ChildRole.TYPE_KEYWORD:
        return TreeUtil.findChild(this, PRIMITIVE_TYPE_BIT_SET);

      case ChildRole.ARGUMENT_LIST:
        return TreeUtil.findChild(this, EXPRESSION_LIST);

      case ChildRole.LBRACKET:
        return TreeUtil.findChild(this, LBRACKET);

      case ChildRole.RBRACKET:
        return TreeUtil.findChild(this, RBRACKET);

      case ChildRole.ARRAY_INITIALIZER:
        if (lastChild.getElementType() == ARRAY_INITIALIZER_EXPRESSION){
          return lastChild;
        }
        else{
          return null;
        }
    }
  }

  public int getChildRole(TreeElement child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == REFERENCE_PARAMETER_LIST) {
      return ChildRole.REFERENCE_PARAMETER_LIST;
    }
    else if (i == NEW_KEYWORD) {
      return ChildRole.NEW_KEYWORD;
    }
    else if (i == DOT) {
      return ChildRole.DOT;
    }
    else if (i == JAVA_CODE_REFERENCE) {
      return ChildRole.TYPE_REFERENCE;
    }
    else if (i == EXPRESSION_LIST) {
      return ChildRole.ARGUMENT_LIST;
    }
    else if (i == LBRACKET) {
      return ChildRole.LBRACKET;
    }
    else if (i == RBRACKET) {
      return ChildRole.RBRACKET;
    }
    else if (i == ARRAY_INITIALIZER_EXPRESSION) {
      if (child == lastChild) {
        return ChildRole.ARRAY_INITIALIZER;
      }
      else if (child == firstChild) {
        return ChildRole.QUALIFIER;
      }
      else {
        return ChildRole.ARRAY_DIMENSION;
      }
    }
    else if (i == ANONYMOUS_CLASS) {
      return ChildRole.ANONYMOUS_CLASS;
    }
    else {
      if (PRIMITIVE_TYPE_BIT_SET.isInSet(child.getElementType())) {
        return ChildRole.TYPE_KEYWORD;
      }
      else if (EXPRESSION_BIT_SET.isInSet(child.getElementType())) {
        return child == firstChild ? ChildRole.QUALIFIER : ChildRole.ARRAY_DIMENSION;
      }
      else {
        return ChildRole.NONE;
      }
    }
  }

  public void accept(PsiElementVisitor visitor){
    visitor.visitNewExpression(this);
  }

  public String toString(){
    return "PsiNewExpression:" + getText();
  }
}

