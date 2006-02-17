package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.psi.impl.cache.RepositoryManager;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import com.intellij.util.text.CharArrayUtil;

public abstract class PsiFileImpl extends PsiFileImplBase{
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiFileImpl");

  private final IElementType myElementType;
  protected final IElementType myContentElementType;

  public PsiFile myOriginalFile = null;

  protected PsiFileImpl(IElementType elementType, IElementType contentElementType, FileViewProvider provider) {
    super(provider, contentElementType.getLanguage());
    myElementType = elementType;
    myContentElementType = contentElementType;
  }

  public TreeElement createContentLeafElement(final char[] text, final int startOffset, final int endOffset, final CharTable table) {
    return Factory.createLeafElement(myContentElementType, text, startOffset, endOffset, -1, table);
  }

  public long getRepositoryId() {
    long id = super.getRepositoryId();
    if (id == -2) {
      RepositoryManager repositoryManager = getRepositoryManager();
      if (repositoryManager != null) {
        id = repositoryManager.getFileId(getViewProvider().getVirtualFile());
      }
      else {
        id = -1;
      }
      super.setRepositoryId(id); // super is important here!
    }
    return id;
  }

  public boolean isRepositoryIdInitialized() {
    return super.getRepositoryId() != -2;
  }

  public synchronized FileElement getTreeElement() {
    if (!getViewProvider().isPhysical() && _getTreeElement() == null) {
      setTreeElement(loadTreeElement());
    }
    return (FileElement)_getTreeElement();
  }

  public void prepareToRepositoryIdInvalidation() {
    if (isRepositoryIdInitialized()) {
      super.prepareToRepositoryIdInvalidation();
    }
  }

  protected boolean isKeepTreeElementByHardReference() {
    return !getViewProvider().isEventSystemEnabled();
  }

  private ASTNode _getTreeElement() {
    return super.getTreeElement();
  }

  public boolean isContentsLoaded() {
    return _getTreeElement() != null;
  }

  public FileElement loadTreeElement() {
    FileElement treeElement = (FileElement)_getTreeElement();
    if (treeElement != null) return treeElement;
    if (getViewProvider().isPhysical() && myManager.isAssertOnFileLoading(getViewProvider().getVirtualFile())) {
      LOG.error("File text loaded " + getViewProvider().getVirtualFile().getPresentableUrl());
    }
    final FileViewProvider viewProvider = getViewProvider();
    // load document outside lock for better performance
    final Document document = viewProvider.getDocument();
    synchronized (PsiLock.LOCK) {
      treeElement = createFileElement(viewProvider.getContents());
      treeElement.putUserData(new Key<Document>("HARD_REFERENCE_TO_DOCUMENT"), document);
      setTreeElement(treeElement);
      treeElement.setPsiElement(this);
    }
    if (getViewProvider().isEventSystemEnabled()) {
      ((PsiDocumentManagerImpl)PsiDocumentManager.getInstance(myManager.getProject())).contentsLoaded(this);
    }
    if (LOG.isDebugEnabled() && getViewProvider().isPhysical()) {
      LOG.debug("Loaded text for file " + getViewProvider().getVirtualFile().getPresentableUrl());
    }
    return treeElement;
  }

  protected FileElement createFileElement(final CharSequence docText) {
    final FileElement treeElement = (FileElement)Factory.createCompositeElement(myElementType);
    char[] chars = CharArrayUtil.fromSequence(docText);
    TreeElement contentElement = createContentLeafElement(chars, 0, docText.length(), treeElement.getCharTable());
    TreeUtil.addChildren(treeElement, contentElement);
    return treeElement;
  }

  public PsiJavaCodeReferenceElement findImportReferenceTo(PsiClass aClass) {
    return null;
  }

  public void unloadContent() {
    LOG.assertTrue(getTreeElement() != null);
    clearCaches();
    getViewProvider().beforeContentsSynchronized();
    setTreeElement(null);
  }

  public void clearCaches() {}

  @SuppressWarnings({"CloneDoesntDeclareCloneNotSupportedException"})
  protected PsiFileImpl clone() {
    final PsiFileImpl clone = (PsiFileImpl)super.clone();

    if(myTreeElementPointer != null){
      // not set by provider in clone
      final FileElement treeClone = (FileElement)calcTreeElement().clone();
      clone.myTreeElementPointer = treeClone; // should not use setTreeElement here because cloned file still have VirtualFile (SCR17963)
      treeClone.setPsiElement(clone);
    }
    return clone;
  }

  public abstract Lexer createLexer();

  public void setTreeElementPointer(FileElement element) {
    myTreeElementPointer = element;
  }
}
