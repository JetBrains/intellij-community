package com.intellij.psi.impl.source.tree;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.PsiTypeElementImpl;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.Helper;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;

//TODO: rename/regroup?
public class SharedImplUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.SharedImplUtil");

  public static PsiElement getParent(TreeElement thisElement) {
    return SourceTreeToPsiMap.treeElementToPsi(thisElement.getTreeParent());
  }

  public static PsiElement getFirstChild(CompositeElement element) {
    if (element.firstChild == null) return null;
    return SourceTreeToPsiMap.treeElementToPsi(element.firstChild.getTransformedFirstOrSelf());
  }

  public static PsiElement getLastChild(CompositeElement element) {
    if (element.lastChild == null) return null;
    return SourceTreeToPsiMap.treeElementToPsi(element.lastChild.getTransformedLastOrSelf());
  }

  public static PsiElement getNextSibling(TreeElement thisElement) {
    final TreeElement treeNext = thisElement.getTreeNext();
    return treeNext != null ? SourceTreeToPsiMap.treeElementToPsi(treeNext.getTransformedFirstOrSelf()) : null;
  }

  public static PsiElement getPrevSibling(TreeElement thisElement) {
    final TreeElement treePrev = thisElement.getTreePrev();
    return treePrev != null ? SourceTreeToPsiMap.treeElementToPsi(treePrev.getTransformedLastOrSelf()) : null;
  }

  public static PsiFile getContainingFile(TreeElement thisElement) {
    TreeElement element;
    for(element = thisElement; element.getTreeParent() != null; element = element.getTreeParent()){
    }

    if (element.getManager() == null) return null; // otherwise treeElementToPsi may crash!
    PsiElement psiElement = SourceTreeToPsiMap.treeElementToPsi(element);
    if(psiElement instanceof DummyHolder) return psiElement.getContainingFile();
    if (!(psiElement instanceof PsiFile)) return null;
    return (PsiFile)psiElement;
  }

  public static boolean isValid(TreeElement thisElement) {
    LOG.assertTrue(thisElement instanceof PsiElement);
    PsiFile file = getContainingFile(thisElement);
    if (file == null) return false;
    return file.isValid();
  }

  public static boolean isWritable(TreeElement thisElement) {
    PsiFile file = (SourceTreeToPsiMap.treeElementToPsi(thisElement)).getContainingFile();
    return file != null ? file.isWritable() : true;
  }

  public static CharTable findCharTableByTree(TreeElement tree){
    while(tree != null){
      final CharTable userData = tree.getUserData(CharTable.CHAR_TABLE_KEY);
      if(userData != null) return userData;
      if(tree instanceof FileElement) return ((FileElement)tree).getCharTable();
      tree = tree.getTreeParent();
    }
    LOG.assertTrue(false, "Invalid root element");
    return null;
  }

  public static PsiElement addRange(PsiElement thisElement, PsiElement first, PsiElement last, TreeElement anchor, Boolean before) throws IncorrectOperationException{
    CheckUtil.checkWritable(thisElement);
    final CharTable table = findCharTableByTree(SourceTreeToPsiMap.psiElementToTree(thisElement));
    FileType fileType = thisElement.getContainingFile().getFileType();
    Project project = thisElement.getProject();
    Helper helper = new Helper(fileType, project);

    TreeElement copyFirst = null;
    TreeElement copyLast = null;
    TreeElement next = SourceTreeToPsiMap.psiElementToTree(last).getTreeNext();
    CompositeElement parent = null;
    for(TreeElement element = SourceTreeToPsiMap.psiElementToTree(first); element != next; element = element.getTreeNext()){
      TreeElement elementCopy = ChangeUtil.copyElement(element, table);
      if (element == first){
        copyFirst = elementCopy;
      }
      if (element == last){
        copyLast = elementCopy;
      }
      if(parent == null) parent = elementCopy.getTreeParent();
      else {
        ChangeUtil.addChild(parent, elementCopy, null);
        helper.normalizeIndent(elementCopy);
      }
    }
    if (copyFirst == null) return null;
    copyFirst = ((CompositeElement)SourceTreeToPsiMap.psiElementToTree(thisElement)).addInternal(copyFirst, copyLast, anchor, before);
    for(TreeElement element = copyFirst; element != null; element = element.getTreeNext()){
      element = ChangeUtil.decodeInformation(element);
      if (element.getTreePrev() == null){
        copyFirst = element;
      }
    }
    return SourceTreeToPsiMap.treeElementToPsi(copyFirst);
  }

  public static PsiType getType(PsiVariable variable){
    PsiTypeElement typeElement = variable.getTypeElement();
    int arrayCount = 0;
    TreeElement name = SourceTreeToPsiMap.psiElementToTree(variable.getNameIdentifier());
  Loop:
    for(TreeElement child = name.getTreeNext(); child != null; child = child.getTreeNext()){
      IElementType i = child.getElementType();
      if (i == ElementType.LBRACKET) {
        arrayCount++;
      }
      else if (i == ElementType.RBRACKET ||
               i == ElementType.WHITE_SPACE ||
               i == ElementType.C_STYLE_COMMENT ||
               i == ElementType.DOC_COMMENT ||
               i == ElementType.END_OF_LINE_COMMENT) {
      }
      else {
        break Loop;
      }
    }
    PsiType type;
    if (!(typeElement instanceof PsiTypeElementImpl)) {
      type = typeElement.getType();
    }
    else {
      type = ((PsiTypeElementImpl)typeElement).getDetachedType(variable);
    }

    for(int i = 0; i < arrayCount; i++){
      type = type.createArrayType();
    }
    return type;
  }

  public static void normalizeBrackets(PsiVariable variable){
    CompositeElement variableElement = (CompositeElement)SourceTreeToPsiMap.psiElementToTree(variable);
    CompositeElement type = (CompositeElement)variableElement.findChildByRole(ChildRole.TYPE);
    LOG.assertTrue(type.getTreeParent() == variableElement);
    TreeElement name = variableElement.findChildByRole(ChildRole.NAME);

    TreeElement firstBracket = null;
    TreeElement lastBracket = null;
    int arrayCount = 0;
    TreeElement element = name;
    while(true){
      element = TreeUtil.skipElements(element.getTreeNext(), ElementType.WHITE_SPACE_OR_COMMENT_BIT_SET);
      if (element == null || element.getElementType() != ElementType.LBRACKET) break;
      if (firstBracket == null) firstBracket = element;
      lastBracket = element;
      arrayCount++;

      element = TreeUtil.skipElements(element.getTreeNext(), ElementType.WHITE_SPACE_OR_COMMENT_BIT_SET);
      if (element == null || element.getElementType() != ElementType.RBRACKET) break;
      lastBracket = element;
    }

    if (firstBracket != null){
      element = firstBracket;
      while(true){
        TreeElement next = element.getTreeNext();
        ChangeUtil.removeChild(variableElement, element);
        if (element == lastBracket) break;
        element = next;
      }

      CompositeElement newType = (CompositeElement)type.clone();
      final CharTable treeCharTable = SharedImplUtil.findCharTableByTree(type);
      for(int i = 0; i < arrayCount; i++){
        CompositeElement newType1 = Factory.createCompositeElement(ElementType.TYPE);
        TreeUtil.addChildren(newType1, newType);

        TreeUtil.addChildren(newType1, Factory.createLeafElement(ElementType.LBRACKET, new char[]{'['}, 0, 1, -1, treeCharTable));
        TreeUtil.addChildren(newType1, Factory.createLeafElement(ElementType.RBRACKET, new char[]{']'}, 0, 1, -1, treeCharTable));
        newType = newType1;
      }
      newType.putUserData(CharTable.CHAR_TABLE_KEY, SharedImplUtil.findCharTableByTree(type));
      ChangeUtil.replaceChild(variableElement, type, newType);
    }
  }
}
