package com.intellij.psi.impl.source;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.dummy.DummyFileSystem;
import com.intellij.psi.*;
import com.intellij.psi.impl.*;
import com.intellij.psi.impl.cache.RepositoryManager;
import com.intellij.psi.impl.file.PsiFileImplUtil;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.text.CharArrayUtil;

public abstract class PsiFileImpl extends NonSlaveRepositoryPsiElement implements PsiFile, PsiFileEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiFileImpl");

  private VirtualFile myFile;
  private String myName; // for myFile == null only

  private final IElementType myElementType;
  private final IElementType myContentElementType;

  private long myModificationStamp;
  private boolean myModificationStampSticky = false;

  protected PsiFile myOriginalFile;
  private boolean myExplicitlySetAsPhysical;
  private boolean myExplicitlySetAsValid;

  protected PsiFileImpl(PsiManagerImpl manager, IElementType elementType, IElementType contentElementType, VirtualFile file) {
    super(manager, -2);
    myFile = file;
    myElementType = elementType;
    myContentElementType = contentElementType;
    myModificationStamp = file.getModificationStamp();
  }

  protected PsiFileImpl(PsiManagerImpl manager,
                        IElementType elementType,
                        IElementType contentElementType,
                        String name,
                        char[] text,
                        int startOffset,
                        int endOffset) {
    super(manager, (RepositoryTreeElement)Factory.createCompositeElement(elementType));
    LOG.assertTrue(name != null);
    myName = name;
    myFile = null;
    myElementType = elementType;
    myContentElementType = contentElementType;
    final FileElement parent = getTreeElement();
    TreeElement contentElement = Factory.createLeafElement(myContentElementType, text, startOffset, endOffset, -1, parent.getCharTable());
    TreeUtil.addChildren(parent, contentElement);
    myModificationStamp = LocalTimeCounter.currentTime();
  }

  protected PsiFileImpl(PsiManagerImpl manager, IElementType elementType) {
    super(manager, (RepositoryTreeElement)Factory.createCompositeElement(elementType));
    myElementType = elementType;
    myName = null;
    myFile = null;
    myContentElementType = null;
    myModificationStamp = LocalTimeCounter.currentTime();
  }

  public long getRepositoryId() {
    long id = super.getRepositoryId();
    if (id == -2) {
      RepositoryManager repositoryManager = getRepositoryManager();
      if (repositoryManager != null) {
        id = repositoryManager.getFileId(myFile);
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

  public FileElement getTreeElement() {
    return (FileElement)_getTreeElement();
  }

  public void prepareToRepositoryIdInvalidation() {
    if (isRepositoryIdInitialized()){
      super.prepareToRepositoryIdInvalidation();
    }
  }

  protected boolean isKeepTreeElementByHardReference() {
    return myFile == null || myExplicitlySetAsPhysical;
  }

  private CompositeElement _getTreeElement() {
    return super.getTreeElement();
  }

  public VirtualFile getVirtualFile() {
    return myFile;
  }

  public void setVirtualFile(VirtualFile file) {
    myFile = file;
    if (file != null) {
      myName = null;
    }
  }

  public boolean isValid() {
    if (myFile == null || myExplicitlySetAsValid) return true; // "dummy" file
    if (!myFile.isValid()) return false;
    return myManager.getFileManager().findFile(myFile) == this;
  }

  public boolean isContentsLoaded() {
    return _getTreeElement() != null;
  }

  public FileElement loadTreeElement() {
    // load document outside lock for better performance
    if (!isPhysical()) {
      return getTreeElement();
    }
    final Document document = FileDocumentManager.getInstance().getDocument(myFile);

    synchronized (PsiLock.LOCK) {
      FileElement treeElement = getTreeElement();
      if (treeElement != null) return treeElement;
      if (myFile != null && myManager.isAssertOnFileLoading(myFile)) {
        LOG.error("File text loaded " + myFile.getPresentableUrl());
      }
      treeElement = (FileElement)Factory.createCompositeElement(myElementType);
      treeElement.setDocument(document);
      final CharSequence docText = ((DocumentEx)document).getCharsSequence();
      char[] chars = CharArrayUtil.fromSequence(docText);

      TreeElement contentElement = Factory.createLeafElement(myContentElementType, chars, 0, docText.length(), -1, treeElement.getCharTable());
      TreeUtil.addChildren(treeElement, contentElement);
      setTreeElement(treeElement);
      treeElement.setPsiElement(this);
      ((PsiDocumentManagerImpl)PsiDocumentManager.getInstance(myManager.getProject())).contentsLoaded(this);
      if (LOG.isDebugEnabled()) {
        LOG.debug("Loaded text for file " + myFile.getPresentableUrl());
      }
      return treeElement;
    }
  }

  public PsiJavaCodeReferenceElement findImportReferenceTo(PsiClass aClass) {
    return null;
  }

  public void setIsPhysicalExplicitly(boolean b) {
    //LOG.assertTrue(ApplicationManagerEx.getApplicationEx().isUnitTestMode());
    myExplicitlySetAsPhysical = b;
  }

  public boolean isExplicitlySetAsPhysical() {
    return myExplicitlySetAsPhysical;
  }

  public void setIsValidExplicitly(boolean b) {
    LOG.assertTrue(ApplicationManager.getApplication().isUnitTestMode());
    myExplicitlySetAsValid = b;
  }

  public void unloadContent() {
    LOG.assertTrue(getTreeElement() != null);

    setTreeElement(null);
  }

  public String getText() {
    return new String(textToCharArray());
  }

  public PsiElement getNextSibling() {
    return SharedPsiElementImplUtil.getNextSibling(this);
  }

  public PsiElement getPrevSibling() {
    return SharedPsiElementImplUtil.getPrevSibling(this);
  }

  public long getModificationStamp() {
    return myModificationStamp;
  }

  public void setModificationStamp(long modificationStamp) {
    myModificationStamp = modificationStamp;
  }

  public void setModificationStampSticky(boolean timeStampSticky) {
    myModificationStampSticky = timeStampSticky;
  }

  public PsiElement[] getOnDemandImports(boolean includeImplicit, boolean checkIncludes) {
    return PsiJavaCodeReferenceElement.EMPTY_ARRAY;
  }

  public PsiClass[] getSingleClassImports(boolean checkIncludes) {
    return PsiClass.EMPTY_ARRAY;
  }

  public String[] getImplicitlyImportedPackages() {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  public PsiJavaCodeReferenceElement[] getImplicitlyImportedPackageReferences() {
    return PsiJavaCodeReferenceElement.EMPTY_ARRAY;
  }

  public void subtreeChanged() {
    if (!myModificationStampSticky) {
      myModificationStamp = LocalTimeCounter.currentTime();
    }
    super.subtreeChanged();
  }

  protected PsiFileImpl clone() {
    PsiFileImpl clone = (PsiFileImpl)super.clone();

    clone.myFile = null;
    clone.myName = getName();
    clone.myExplicitlySetAsPhysical = false;
    if (getVirtualFile() != null) {
      clone.myOriginalFile = this;
    }
    else if (myOriginalFile != null) {
      clone.myOriginalFile = myOriginalFile;
    }

    return clone;
  }

  public String getName() {
    return myFile != null ? myFile.getName() : myName;
  }

  public PsiElement setName(String name) throws IncorrectOperationException {
    checkSetName(name);

    if (myFile == null) {
      myName = name;
      return this; // not absolutely correct - might change type
    }

    return PsiFileImplUtil.setName(this, name);
  }

  public void checkSetName(String name) throws IncorrectOperationException {
    if (myFile == null) return;
    PsiFileImplUtil.checkSetName(this, name);
  }

  public boolean isWritable() {
    return myFile != null ? myFile.isWritable() : true;
  }

  public PsiElement getParent() {
    return getContainingDirectory();
  }

  public PsiDirectory getContainingDirectory() {
    if (myFile == null) return null;
    final VirtualFile parentFile = myFile.getParent();
    if (parentFile == null) return null;
    return getManager().findDirectory(parentFile);
  }

  public PsiFile getContainingFile() {
    return this;
  }

  public void delete() throws IncorrectOperationException {
    checkDelete();
    PsiFileImplUtil.doDelete(this);
  }

  public void checkDelete() throws IncorrectOperationException {
    if (myFile == null) {
      throw new IncorrectOperationException();
    }
    CheckUtil.checkWritable(this);
  }

  public PsiFile getOriginalFile() {
    return myOriginalFile;
  }

  public boolean canContainJavaCode() {
    return false;
  }

  public PsiFile[] getPsiRoots() {
    return new PsiFile[]{this};
  }

  public PsiFile createPseudoPhysicalCopy() {
    PsiFileImpl copy = (PsiFileImpl)copy();
    copy.setIsPhysicalExplicitly(true); //?
    return copy;
  }

  public IElementType getContentElementType() {
    return myContentElementType;
  }

  public <T> T getCopyableUserData(Key<T> key) {
    return getCopyableUserDataImpl(key);
  }

  public <T> void putCopyableUserData(Key<T> key, T value) {
    putCopyableUserDataImpl(key, value);
  }

  public boolean isPhysical() {
    return myExplicitlySetAsPhysical || myFile != null && !(myFile.getFileSystem() instanceof DummyFileSystem);
  }

  public abstract Lexer createLexer();
}
