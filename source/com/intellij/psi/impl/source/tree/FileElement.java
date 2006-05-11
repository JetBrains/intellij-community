package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiLock;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.CharTableImpl;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.SrcRepositoryPsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;

public class FileElement extends RepositoryTreeElement{
  private CharTable myCharTable = new CharTableImpl();
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.FileElement");

  public CharTable getCharTable() {
    return myCharTable;
  }

  public FileElement(IElementType type) {
    super(type);
  }

  public PsiManager getManager() {
    final PsiManager manager = getUserData(MANAGER_KEY);
    if (manager == null) {
      if(parent != null) return parent.getManager();
      else return SourceTreeToPsiMap.treeElementToPsi(this).getManager(); //TODO: cache?
    }
    else {
      return manager;
    }
  }

  public ASTNode copyElement() {
    SrcRepositoryPsiElement psiElement = getPsiElement();
    SrcRepositoryPsiElement psiElementCopy = (SrcRepositoryPsiElement)psiElement.copy();
    return psiElementCopy.getTreeElement();
  }

  @NotNull
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

  public void replaceChildInternal(ASTNode child, TreeElement newElement) {
    if (newElement.getElementType() == ElementType.IMPORT_LIST) {
      LOG.assertTrue(child.getElementType() == ElementType.IMPORT_LIST);
      if (newElement.getFirstChildNode() == null) { //empty import list
        ASTNode next = child.getTreeNext();
        if (next.getElementType() == ElementType.WHITE_SPACE) {
          removeChild(next);
        }
      }
    }
    super.replaceChildInternal(child, newElement);
  }
}
