package com.intellij.psi.impl.source.tree;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiLock;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.CharTableImpl;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.SrcRepositoryPsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;

public abstract class FileElement extends RepositoryTreeElement{
  private Document myDocument; // only to hold document
  private CharTable myCharTable = new CharTableImpl();
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.FileElement");

  public CharTable getCharTable() {
    return myCharTable;
  }

  protected FileElement(IElementType type) {
    super(type);
  }

  public void setDocument(Document document){
    myDocument = document;
  }

  public PsiManager getManager() {
    final PsiManager manager = getUserData(MANAGER_KEY);
    if (manager == null) {
      return SourceTreeToPsiMap.treeElementToPsi(this).getManager(); //TODO: cache?
    }
    else {
      return manager;
    }
  }

  public TreeElement copyElement() {
    SrcRepositoryPsiElement psiElement = getPsiElement();
    SrcRepositoryPsiElement psiElementCopy = (SrcRepositoryPsiElement)psiElement.copy();
    return psiElementCopy.getTreeElement();
  }

  public Object clone() {
    FileElement clone = (FileElement)super.clone();
    clone.myDocument = null;
    return clone;
  }

  public char[] textToCharArray() {
    synchronized (PsiLock.LOCK) {
      final int textLength = getTextLength();
      char[] buffer = new char[textLength];
      SourceUtil.toBuffer(this, buffer, 0);
      return buffer;
    }
  }

  public void setCharTable(CharTable table) {
    myCharTable = table;
  }

  public void replaceChildInternal(TreeElement child, TreeElement newElement) {
    if (newElement.getElementType() == ElementType.IMPORT_LIST) {
      LOG.assertTrue(child.getElementType() == ElementType.IMPORT_LIST);
      if (((CompositeElement)newElement).firstChild == null) { //empty import list
        TreeElement next = child.getTreeNext();
        if (next.getElementType() == ElementType.WHITE_SPACE) {
          ChangeUtil.removeChild(this, next);
        }
      }
    }
    super.replaceChildInternal(child, newElement);
  }
}
