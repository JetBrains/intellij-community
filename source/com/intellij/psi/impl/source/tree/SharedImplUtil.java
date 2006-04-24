package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.GeneratedMarkerVisitor;
import com.intellij.psi.impl.source.PsiTypeElementImpl;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.Helper;import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;

//TODO: rename/regroup?

public class SharedImplUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.SharedImplUtil");

  public static PsiElement getParent(ASTNode thisElement) {
    return SourceTreeToPsiMap.treeElementToPsi(thisElement.getTreeParent());
  }

  public static PsiElement getFirstChild(ASTNode element) {
    final TreeElement firstChild = (TreeElement)element.getFirstChildNode();
    return firstChild != null ? SourceTreeToPsiMap.treeElementToPsi(firstChild.getTransformedFirstOrSelf()) : null;
  }

  public static PsiElement getLastChild(ASTNode element) {
    final TreeElement lastChild = (TreeElement)element.getLastChildNode();
    return lastChild != null ? lastChild.getTransformedLastOrSelf().getPsi() : null;
  }

  public static PsiElement getNextSibling(ASTNode thisElement) {
    final TreeElement treeNext = (TreeElement)thisElement.getTreeNext();
    final PsiElement psiElement = treeNext != null ? SourceTreeToPsiMap.treeElementToPsi(treeNext.getTransformedFirstOrSelf()) : null;
    if (psiElement != null && psiElement.getNode() != null && psiElement.getNode().getElementType() == ElementType.REFORMAT_MARKER)
      return getNextSibling(psiElement.getNode());
    return psiElement;
  }

  public static PsiElement getPrevSibling(ASTNode thisElement) {
    final TreeElement treePrev = (TreeElement)thisElement.getTreePrev();
    final PsiElement psiElement = treePrev != null ? SourceTreeToPsiMap.treeElementToPsi(treePrev.getTransformedLastOrSelf()) : null;
    if (psiElement != null && psiElement.getNode() != null && psiElement.getNode().getElementType() == ElementType.REFORMAT_MARKER)
      return getPrevSibling(psiElement.getNode());
    return psiElement;
  }

  public static PsiFile getContainingFile(ASTNode thisElement) {
    ASTNode element;
    for (element = thisElement; element.getTreeParent() != null; element = element.getTreeParent()) {
    }

    PsiElement psiElement = element.getPsi();
    if (!(psiElement instanceof PsiFile)) return null;
    return psiElement.getContainingFile();
  }

  public static boolean isValid(ASTNode thisElement) {
    LOG.assertTrue(thisElement instanceof PsiElement);
    PsiFile file = getContainingFile(thisElement);
    if (file == null) return false;
    return file.isValid();
  }

  public static boolean isWritable(ASTNode thisElement) {
    PsiFile file = (SourceTreeToPsiMap.treeElementToPsi(thisElement)).getContainingFile();
    return file != null ? file.isWritable() : true;
  }

  public static CharTable findCharTableByTree(ASTNode tree) {
    while (tree != null) {
      final CharTable userData = tree.getUserData(CharTable.CHAR_TABLE_KEY);
      if (userData != null) return userData;
      if (tree instanceof FileElement) return ((FileElement)tree).getCharTable();
      tree = tree.getTreeParent();
    }
    LOG.assertTrue(false, "Invalid root element");
    return null;
  }

  public static PsiElement addRange(PsiElement thisElement,
                                    PsiElement first,
                                    PsiElement last,
                                    ASTNode anchor,
                                    Boolean before) throws IncorrectOperationException {
    CheckUtil.checkWritable(thisElement);
    final CharTable table = findCharTableByTree(SourceTreeToPsiMap.psiElementToTree(thisElement));
    FileType fileType = thisElement.getContainingFile().getFileType();
    Project project = thisElement.getProject();
    Helper helper = new Helper(fileType, project);

    TreeElement copyFirst = null;
    ASTNode copyLast = null;
    ASTNode next = SourceTreeToPsiMap.psiElementToTree(last).getTreeNext();
    ASTNode parent = null;
    for (ASTNode element = SourceTreeToPsiMap.psiElementToTree(first); element != next; element = element.getTreeNext()) {
      TreeElement elementCopy = ChangeUtil.copyElement((TreeElement)element, table);
      if (element == first.getNode()) {
        copyFirst = elementCopy;
      }
      if (element == last.getNode()) {
        copyLast = elementCopy;
      }
      if (parent == null) {
        parent = elementCopy.getTreeParent();
      }
      else {
        if(elementCopy.getElementType() == ElementType.WHITE_SPACE)
          CodeEditUtil.setNodeGenerated(elementCopy, true);
        parent.addChild(elementCopy, null);
        //helper.normalizeIndent(elementCopy);
      }
    }
    if (copyFirst == null) return null;
    copyFirst = ((CompositeElement)SourceTreeToPsiMap.psiElementToTree(thisElement)).addInternal(copyFirst, copyLast, anchor, before);
    for (TreeElement element = copyFirst; element != null; element = element.getTreeNext()) {
      element = ChangeUtil.decodeInformation(element);
      if (element.getTreePrev() == null) {
        copyFirst = element;
      }
    }
    return SourceTreeToPsiMap.treeElementToPsi(copyFirst);
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
      element = TreeUtil.skipElements(element.getTreeNext(), ElementType.WHITE_SPACE_OR_COMMENT_BIT_SET);
      if (element == null || element.getElementType() != ElementType.LBRACKET) break;
      if (firstBracket == null) firstBracket = element;
      lastBracket = element;
      arrayCount++;

      element = TreeUtil.skipElements(element.getTreeNext(), ElementType.WHITE_SPACE_OR_COMMENT_BIT_SET);
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

        TreeUtil.addChildren(newType1, Factory.createLeafElement(ElementType.LBRACKET, new char[]{'['}, 0, 1, -1, treeCharTable));
        TreeUtil.addChildren(newType1, Factory.createLeafElement(ElementType.RBRACKET, new char[]{']'}, 0, 1, -1, treeCharTable));
        newType = newType1;
        newType.acceptTree(new GeneratedMarkerVisitor());
      }
      newType.putUserData(CharTable.CHAR_TABLE_KEY, SharedImplUtil.findCharTableByTree(type));
      variableElement.replaceChild(type, newType);
    }
  }

  public static PsiManager getManagerByTree(final ASTNode node) {
    if(node instanceof FileElement) return node.getPsi().getManager();
    return node.getTreeParent().getPsi().getManager();
  }

  public static void setInitializer(PsiVariable variable, final PsiExpression initializer) throws IncorrectOperationException {
    PsiExpression oldInitializer = variable.getInitializer();
    if (initializer == null) {
      if (oldInitializer != null) {
        oldInitializer.delete();
      }
      return;
    }
    CompositeElement variableElement = (CompositeElement)variable.getNode();
    ASTNode eq = variableElement.findChildByRole(ChildRole.INITIALIZER_EQ);
    if (eq == null) {
      final CharTable charTable = findCharTableByTree(variableElement);
      eq = Factory.createSingleLeafElement(JavaTokenType.EQ, "=".toCharArray(), 0, 1, charTable, variable.getManager());
      variableElement.addInternal((TreeElement)eq, eq, variable.getNameIdentifier().getNode(), Boolean.FALSE);
      eq = variableElement.findChildByRole(ChildRole.INITIALIZER_EQ);
    }
    variable.addAfter(initializer, eq.getPsi());
  }
}
