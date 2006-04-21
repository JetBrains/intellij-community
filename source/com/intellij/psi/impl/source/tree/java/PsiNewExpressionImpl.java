package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

public class PsiNewExpressionImpl extends CompositePsiElement implements PsiNewExpression {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiNewExpressionImpl");

  public PsiNewExpressionImpl() {
    super(NEW_EXPRESSION);
  }

  public PsiType getType(){
    PsiType type = null;
    for(ASTNode child = getFirstChildNode(); child != null; child = child.getTreeNext()){
      if (child.getElementType() == JAVA_CODE_REFERENCE){
        LOG.assertTrue(type == null);
        type = new PsiClassReferenceType((PsiJavaCodeReferenceElement)SourceTreeToPsiMap.treeElementToPsi(child));
      }
      else if (PRIMITIVE_TYPE_BIT_SET.contains(child.getElementType())){
        LOG.assertTrue(type == null);
        type = getManager().getElementFactory().createPrimitiveType(child.getText());
      }
      else if (child.getElementType() == LBRACKET){
        LOG.assertTrue(type != null);
        type = type.createArrayType();
      }
      else if (child.getElementType() == ANONYMOUS_CLASS){
        PsiElementFactory factory = getManager().getElementFactory();
        type = factory.createType((PsiClass) SourceTreeToPsiMap.treeElementToPsi(child));
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

  @NotNull
  public PsiExpression[] getArrayDimensions() {
    PsiExpression[] expressions = getChildrenAsPsiElements(ARRAY_DIMENSION_BIT_SET, PSI_EXPRESSION_ARRAY_CONSTRUCTOR);
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

  @NotNull
  public JavaResolveResult resolveMethodGenerics() {
    ASTNode classRef = findChildByRole(ChildRole.TYPE_REFERENCE);
    if (classRef != null){
      ASTNode argumentList = TreeUtil.skipElements(classRef.getTreeNext(), WHITE_SPACE_OR_COMMENT_BIT_SET);
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
      ASTNode anonymousClass = TreeUtil.findChild(this, ANONYMOUS_CLASS);
      if (anonymousClass != null) {
        PsiType aClass = ((PsiAnonymousClass)SourceTreeToPsiMap.treeElementToPsi(anonymousClass)).getBaseClassType();
        if (aClass != null) {
          ASTNode argumentList = TreeUtil.findChild(anonymousClass, EXPRESSION_LIST);
          return getManager().getResolveHelper().resolveConstructor((PsiClassType)aClass,
                                                                    (PsiExpressionList)SourceTreeToPsiMap.treeElementToPsi(argumentList),
                                                                    this);
        }
      }
    }

    return JavaResolveResult.EMPTY;
  }

  public PsiExpression getQualifier() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.QUALIFIER);
  }

  @NotNull
  public PsiReferenceParameterList getTypeArgumentList() {
    return (PsiReferenceParameterList) findChildByRoleAsPsiElement(ChildRole.REFERENCE_PARAMETER_LIST);
  }

  @NotNull
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
    ASTNode anonymousClass = TreeUtil.findChild(this, ANONYMOUS_CLASS);
    if (anonymousClass == null) return null;
    return (PsiAnonymousClass)SourceTreeToPsiMap.treeElementToPsi(anonymousClass);
  }

  public void deleteChildInternal(ASTNode child) {
    if (getChildRole(child) == ChildRole.QUALIFIER){
      ASTNode dot = findChildByRole(ChildRole.DOT);
      super.deleteChildInternal(child);
      deleteChildInternal(dot);
    }
    else{
      super.deleteChildInternal(child);
    }
  }

  public ASTNode findChildByRole(int role){
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.REFERENCE_PARAMETER_LIST:
        return TreeUtil.findChild(this, REFERENCE_PARAMETER_LIST);

      case ChildRole.QUALIFIER:
        TreeElement firstChild = getFirstChildNode();
        if (firstChild != null && firstChild.getElementType() != NEW_KEYWORD) {
          while(firstChild != null && firstChild.getElementType() == ElementType.REFORMAT_MARKER) firstChild = firstChild.getTreeNext();
          return firstChild.getElementType() != NEW_KEYWORD ? firstChild : null;
        }
        else return null;

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
        if (getLastChildNode().getElementType() == ARRAY_INITIALIZER_EXPRESSION){
          return getLastChildNode();
        }
        else{
          return null;
        }
    }
  }

  public int getChildRole(ASTNode child) {
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
      if (child == getLastChildNode()) {
        return ChildRole.ARRAY_INITIALIZER;
      }
      else if (child == getFirstChildNode()) {
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
      if (PRIMITIVE_TYPE_BIT_SET.contains(child.getElementType())) {
        return ChildRole.TYPE_KEYWORD;
      }
      else if (EXPRESSION_BIT_SET.contains(child.getElementType())) {
        return child == getFirstChildNode() ? ChildRole.QUALIFIER : ChildRole.ARRAY_DIMENSION;
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

