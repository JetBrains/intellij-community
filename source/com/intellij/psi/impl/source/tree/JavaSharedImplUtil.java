package com.intellij.psi.impl.source.tree;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.PsiTypeElementImpl;
import com.intellij.psi.impl.GeneratedMarkerVisitor;
import com.intellij.lang.ASTNode;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import com.intellij.openapi.diagnostic.Logger;

public class JavaSharedImplUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.JavaSharedImplUtil");

  private JavaSharedImplUtil() {
  }

  public static PsiType getType(PsiVariable variable) {
    PsiTypeElement typeElement = variable.getTypeElement();
    PsiIdentifier nameIdentifier = variable.getNameIdentifier();
    return getType(typeElement, nameIdentifier, variable);
  }

  //context == null means no detached type should be created
  public static PsiType getType(PsiTypeElement typeElement, PsiElement anchor, PsiElement context) {
    int arrayCount = 0;
    ASTNode name = SourceTreeToPsiMap.psiElementToTree(anchor);
    for (ASTNode child = name.getTreeNext(); child != null; child = child.getTreeNext()) {
      IElementType i = child.getElementType();
      if (i == ElementType.LBRACKET) {
        arrayCount++;
      }
      else if (i != ElementType.RBRACKET &&
               i != ElementType.WHITE_SPACE &&
               i != ElementType.C_STYLE_COMMENT &&
               i != JavaDocElementType.DOC_COMMENT &&
               i != JavaTokenType.DOC_COMMENT &&
               i != ElementType.END_OF_LINE_COMMENT) {
        break;
      }
    }
    PsiType type;
    if (context != null && typeElement instanceof PsiTypeElementImpl) {
      type = ((PsiTypeElementImpl)typeElement).getDetachedType(context);
    }
    else {
      type = typeElement.getType();
    }

    for (int i = 0; i < arrayCount; i++) {
      type = type.createArrayType();
    }
    return type;
  }

  public static void normalizeBrackets(PsiVariable variable) {
    CompositeElement variableElement = (CompositeElement)SourceTreeToPsiMap.psiElementToTree(variable);
    ASTNode type = variableElement.findChildByRole(ChildRole.TYPE);
    LOG.assertTrue(type.getTreeParent() == variableElement);
    ASTNode name = variableElement.findChildByRole(ChildRole.NAME);

    ASTNode firstBracket = null;
    ASTNode lastBracket = null;
    int arrayCount = 0;
    ASTNode element = name;
    while (true) {
      element = TreeUtil.skipElements(element.getTreeNext(), StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET);
      if (element == null || element.getElementType() != ElementType.LBRACKET) break;
      if (firstBracket == null) firstBracket = element;
      lastBracket = element;
      arrayCount++;

      element = TreeUtil.skipElements(element.getTreeNext(), StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET);
      if (element == null || element.getElementType() != ElementType.RBRACKET) break;
      lastBracket = element;
    }

    if (firstBracket != null) {
      element = firstBracket;
      while (true) {
        ASTNode next = element.getTreeNext();
        variableElement.removeChild(element);
        if (element == lastBracket) break;
        element = next;
      }

      CompositeElement newType = (CompositeElement)type.clone();
      final CharTable treeCharTable = SharedImplUtil.findCharTableByTree(type);
      for (int i = 0; i < arrayCount; i++) {
        CompositeElement newType1 = Factory.createCompositeElement(ElementType.TYPE);
        TreeUtil.addChildren(newType1, newType);

        TreeUtil.addChildren(newType1, Factory.createLeafElement(ElementType.LBRACKET, "[", 0, 1, treeCharTable));
        TreeUtil.addChildren(newType1, Factory.createLeafElement(ElementType.RBRACKET, "]", 0, 1, treeCharTable));
        newType = newType1;
        newType.acceptTree(new GeneratedMarkerVisitor());
      }
      newType.putUserData(CharTable.CHAR_TABLE_KEY, SharedImplUtil.findCharTableByTree(type));
      variableElement.replaceChild(type, newType);
    }
  }

  public static void setInitializer(PsiVariable variable, final PsiExpression initializer) throws IncorrectOperationException {
    PsiExpression oldInitializer = variable.getInitializer();
    if (oldInitializer != null) {
      oldInitializer.delete();
    }
    if (initializer == null) {
      return;
    }
    CompositeElement variableElement = (CompositeElement)variable.getNode();
    ASTNode eq = variableElement.findChildByRole(ChildRole.INITIALIZER_EQ);
    if (eq == null) {
      final CharTable charTable = SharedImplUtil.findCharTableByTree(variableElement);
      eq = Factory.createSingleLeafElement(JavaTokenType.EQ, "=", 0, 1, charTable, variable.getManager());
      variableElement.addInternal((TreeElement)eq, eq, variable.getNameIdentifier().getNode(), Boolean.FALSE);
      eq = variableElement.findChildByRole(ChildRole.INITIALIZER_EQ);
    }
    variable.addAfter(initializer, eq.getPsi());
  }
}
