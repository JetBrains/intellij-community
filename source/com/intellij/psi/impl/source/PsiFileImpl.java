package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.dummy.DummyFileSystem;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.*;
import com.intellij.psi.impl.*;
import com.intellij.psi.impl.cache.RepositoryManager;
import com.intellij.psi.impl.file.PsiFileImplUtil;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

public abstract class PsiFileImpl extends NonSlaveRepositoryPsiElement implements PsiFileEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiFileImpl");

  private final IElementType myElementType;
  protected final IElementType myContentElementType;

  protected PsiFile myOriginalFile = null;
  private boolean myExplicitlySetAsValid = false;
  protected FileViewProvider myViewProvider = null;

  protected PsiFileImpl(IElementType elementType, IElementType contentElementType, FileViewProvider provider) {
    super((PsiManagerImpl)provider.getManager(), !provider.isPhysical() ? -1 : -2);
    myElementType = elementType;
    myContentElementType = contentElementType;
    myViewProvider = provider;
  }

  public TreeElement createContentLeafElement(final char[] text, final int startOffset, final int endOffset, final CharTable table) {
    return Factory.createLeafElement(myContentElementType, text, startOffset, endOffset, -1, table);
  }

  protected PsiFileImpl(PsiManagerImpl manager, IElementType elementType) {
    super(manager, (RepositoryTreeElement)Factory.createCompositeElement(elementType));
    myElementType = elementType;
    myContentElementType = null;
  }

  public long getRepositoryId() {
    long id = super.getRepositoryId();
    if (id == -2) {
      RepositoryManager repositoryManager = getRepositoryManager();
      if (repositoryManager != null) {
        id = repositoryManager.getFileId(myViewProvider.getVirtualFile());
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
    if(!getViewProvider().isPhysical() && _getTreeElement() == null)
      setTreeElement(loadTreeElement());
    return (FileElement)_getTreeElement();
  }

  public void prepareToRepositoryIdInvalidation() {
    if (isRepositoryIdInitialized()) {
      super.prepareToRepositoryIdInvalidation();
    }
  }

  protected boolean isKeepTreeElementByHardReference() {
    return !myViewProvider.isEventSystemEnabled();
  }

  private ASTNode _getTreeElement() {
    return super.getTreeElement();
  }

  public VirtualFile getVirtualFile() {
    return myViewProvider.isEventSystemEnabled() ? myViewProvider.getVirtualFile() : null;
  }

  public boolean isValid() {
    if (!myViewProvider.isPhysical() || myExplicitlySetAsValid) return true; // "dummy" file
    return getViewProvider().getVirtualFile().isValid();
  }

  public boolean isContentsLoaded() {
    return _getTreeElement() != null;
  }

  public FileElement loadTreeElement() {
    // load document outside lock for better performance
    synchronized (PsiLock.LOCK) {
      FileElement treeElement = (FileElement)_getTreeElement();
      if (treeElement != null) return treeElement;
      if (myViewProvider.isPhysical() && myManager.isAssertOnFileLoading(getViewProvider().getVirtualFile())) {
        LOG.error("File text loaded " + getViewProvider().getVirtualFile().getPresentableUrl());
      }
      final FileViewProvider viewProvider = getViewProvider();
      final Document document = viewProvider.getDocument();
      treeElement = createFileElement(viewProvider.getContents());
      treeElement.putUserData(new Key<Document>("HARD_REFERENCE_TO_DOCUMENT"), document);
      setTreeElement(treeElement);
      treeElement.setPsiElement(this);
      if (myViewProvider.isEventSystemEnabled()) ((PsiDocumentManagerImpl)PsiDocumentManager.getInstance(myManager.getProject())).contentsLoaded(this);
      if (myViewProvider.isPhysical() && LOG.isDebugEnabled()) {
        LOG.debug("Loaded text for file " + getViewProvider().getVirtualFile().getPresentableUrl());
      }
      return treeElement;
    }
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

  public void setIsValidExplicitly(boolean b) {
    LOG.assertTrue(ApplicationManager.getApplication().isUnitTestMode());
    myExplicitlySetAsValid = b;
  }

  public void unloadContent() {
    LOG.assertTrue(getTreeElement() != null);
    clearCaches();
    setTreeElement(null);
  }

  public void clearCaches() {}

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
    return getViewProvider().getModificationStamp();
  }

  @NotNull
  public PsiElement[] getOnDemandImports(boolean includeImplicit, boolean checkIncludes) {
    return PsiElement.EMPTY_ARRAY;
  }

  @NotNull
  public PsiClass[] getSingleClassImports(boolean checkIncludes) {
    return PsiClass.EMPTY_ARRAY;
  }

  @NotNull
  public String[] getImplicitlyImportedPackages() {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @NotNull
  public PsiJavaCodeReferenceElement[] getImplicitlyImportedPackageReferences() {
    return PsiJavaCodeReferenceElement.EMPTY_ARRAY;
  }

  public void subtreeChanged() {
    myViewProvider.rootChanged(this);
    super.subtreeChanged();
  }

  @SuppressWarnings({"CloneDoesntDeclareCloneNotSupportedException"})
  protected PsiFileImpl clone() {
    FileViewProvider provider = myViewProvider.clone();
    PsiFileImpl clone = (PsiFileImpl)provider.getPsi(getLanguage());

    HashMap<Key,Object> copyableMap = getUserData(COPYABLE_USER_MAP_KEY);
    if (copyableMap != null){
      final HashMap<Key,Object> mapclone = (HashMap<Key, Object>)copyableMap.clone();
      clone.putUserData(COPYABLE_USER_MAP_KEY, mapclone);
    }
    final FileElement treeClone = (FileElement)calcTreeElement().clone();
    clone.myTreeElementPointer = treeClone; // should not use setTreeElement here because cloned file still have VirtualFile (SCR17963)
    treeClone.setPsiElement(clone);

    if (getViewProvider().isEventSystemEnabled()) {
      clone.myOriginalFile = this;
    }
    else if (myOriginalFile != null) {
      clone.myOriginalFile = myOriginalFile;
    }

    return clone;
  }

  @NotNull public String getName() {
    return getViewProvider().getVirtualFile().getName();
  }

  public PsiElement setName(String name) throws IncorrectOperationException {
    checkSetName(name);
    subtreeChanged();
    return PsiFileImplUtil.setName(this, name);
  }

  public void checkSetName(String name) throws IncorrectOperationException {
    if (!myViewProvider.isEventSystemEnabled()) return;
    PsiFileImplUtil.checkSetName(this, name);
  }

  public boolean isWritable() {
    return getViewProvider().getVirtualFile().isWritable();
  }

  public PsiElement getParent() {
    return getContainingDirectory();
  }

  public PsiDirectory getContainingDirectory() {
    final VirtualFile parentFile = getViewProvider().getVirtualFile().getParent();
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
    if (!myViewProvider.isEventSystemEnabled()) {
      throw new IncorrectOperationException();
    }
    CheckUtil.checkWritable(this);
  }

  public PsiFile getOriginalFile() {
    return myOriginalFile;
  }

  public void setOriginalFile(final PsiFile originalFile) {
    if (originalFile.getOriginalFile() != null) {
      myOriginalFile = originalFile.getOriginalFile();
    }
    else {
      myOriginalFile = originalFile;
    }
  }

  public boolean canContainJavaCode() {
    return false;
  }

  @NotNull
  public PsiFile[] getPsiRoots() {
    return new PsiFile[]{this};
  }

  public <T> T getCopyableUserData(Key<T> key) {
    return getCopyableUserDataImpl(key);
  }

  public <T> void putCopyableUserData(Key<T> key, T value) {
    putCopyableUserDataImpl(key, value);
  }

  public boolean isPhysical() {
    // TODO[ik] remove this shit with dummy file system
    return myViewProvider.isEventSystemEnabled();
  }

  public abstract Lexer createLexer();

  @NotNull
  public Language getLanguage() {
    final FileType fileType = getFileType();
    return fileType instanceof LanguageFileType ? ((LanguageFileType)fileType).getLanguage() : Language.ANY;
  }

  public FileViewProvider getViewProvider() {
    return myViewProvider;
  }
}
