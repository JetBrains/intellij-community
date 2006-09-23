package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.*;
import com.intellij.psi.impl.cache.RepositoryManager;
import com.intellij.psi.impl.cache.impl.CacheUtil;
import com.intellij.psi.impl.file.PsiFileImplUtil;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class PsiFileImpl extends NonSlaveRepositoryPsiElement implements PsiFileEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiFileImpl");

  private IElementType myElementType;
  protected IElementType myContentElementType;

  public PsiFile myOriginalFile = null;
  private boolean myExplicitlySetAsValid = false;
  private FileViewProvider myViewProvider;

  protected PsiFileImpl(IElementType elementType, IElementType contentElementType, FileViewProvider provider) {
    this(provider);
    init(elementType, contentElementType);
  }

  protected PsiFileImpl( FileViewProvider provider ) {
    super((PsiManagerImpl)provider.getManager(), !provider.isPhysical() ? -1 : -2);
    myViewProvider = provider;
  }

  /**
   * For Irida API compatibility
   */
  @Deprecated protected PsiFileImpl(PsiManagerImpl manager) {
    super(manager, -2);
  }

  /**
   * For Irida API compatibility
   */
  @Deprecated public void setViewProvider(FileViewProvider provider) {
    LOG.assertTrue(myViewProvider == null);
    myViewProvider = provider;
  }

  protected void init(final IElementType elementType, final IElementType contentElementType) {
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

  public VirtualFile getVirtualFile() {
    return getViewProvider().isEventSystemEnabled() ? getViewProvider().getVirtualFile() : null;
  }

  public boolean isValid() {
    if (!getViewProvider().isPhysical() || myExplicitlySetAsValid) return true; // "dummy" file
    final VirtualFile vFile = getViewProvider().getVirtualFile();
    return vFile.isValid() && isPsiUpToDate(vFile);
  }

  protected boolean isPsiUpToDate(VirtualFile vFile) {
    final FileViewProvider viewProvider = myManager.findViewProvider(vFile);
    final boolean isValid = viewProvider.getPsi(viewProvider.getBaseLanguage()) == this;

    return isValid;
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
    final Document document = viewProvider.isEventSystemEnabled() ? viewProvider.getDocument() : null;
    synchronized (PsiLock.LOCK) {
      treeElement = createFileElement(viewProvider.getContents());
      if (document != null) {
        treeElement.putUserData(new Key<Document>("HARD_REFERENCE_TO_DOCUMENT"), document);
      }
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
    if (getUserData(CacheUtil.CACHE_COPY_KEY) == Boolean.TRUE) {
      treeElement.setCharTable(new IdentityCharTable());
    }
    
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
    myViewProvider.beforeContentsSynchronized();
    setTreeElement(null);
  }

  public void clearCaches() {}

  public String getText() {
    return getViewProvider().getContents().toString();
  }

  public int getTextLength() {
    final ASTNode tree = _getTreeElement();
    if (tree != null) return tree.getTextLength();

    return getViewProvider().getContents().length();
  }

  public TextRange getTextRange() {
    return new TextRange(0, getTextLength());
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

  public void subtreeChanged() {
    super.subtreeChanged();
    clearCaches();
    getViewProvider().rootChanged(this);
  }

  @SuppressWarnings({"CloneDoesntDeclareCloneNotSupportedException"})
  protected PsiFileImpl clone() {
    FileViewProvider provider = getViewProvider().clone();
    PsiFileImpl clone = (PsiFileImpl)provider.getPsi(getLanguage());

    Map<Key,Object> copyableMap = getUserData(COPYABLE_USER_MAP_KEY);
    if (copyableMap != null){
      final Map<Key,Object> mapclone = ((THashMap<Key, Object>)copyableMap).clone();
      clone.putUserData(COPYABLE_USER_MAP_KEY, mapclone);
    }
    if(myTreeElementPointer != null){
      // not set by provider in clone
      final FileElement treeClone = (FileElement)calcTreeElement().clone();
      clone.myTreeElementPointer = treeClone; // should not use setTreeElement here because cloned file still have VirtualFile (SCR17963)
      treeClone.setPsiElement(clone);
    }

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
    if (!getViewProvider().isEventSystemEnabled()) return;
    PsiFileImplUtil.checkSetName(this, name);
  }

  public boolean isWritable() {
    return getViewProvider().getVirtualFile().isWritable() && getUserData(CacheUtil.CACHE_COPY_KEY) != Boolean.TRUE;
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
    if (!getViewProvider().isEventSystemEnabled()) {
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

  @NotNull
  public PsiFile[] getPsiRoots() {
    final Set<Language> languages = getViewProvider().getPrimaryLanguages();
    final List<PsiFile> roots = new ArrayList<PsiFile>();
    for (Language language : languages) {
      roots.add(getViewProvider().getPsi(language));
    }
    return roots.toArray(new PsiFile[roots.size()]);
  }

  public <T> T getCopyableUserData(Key<T> key) {
    return getCopyableUserDataImpl(key);
  }

  public <T> void putCopyableUserData(Key<T> key, T value) {
    putCopyableUserDataImpl(key, value);
  }

  public boolean isPhysical() {
    // TODO[ik] remove this shit with dummy file system
    return getViewProvider().isEventSystemEnabled();
  }

  public abstract Lexer createLexer();

  @NotNull
  public Language getLanguage() {
    return myContentElementType.getLanguage();
  }

  @NotNull
  public FileViewProvider getViewProvider() {
    return myViewProvider;
  }

  public void setTreeElementPointer(FileElement element) {
    myTreeElementPointer = element;
  }

  public PsiElement findElementAt(int offset) {
    return getViewProvider().findElementAt(offset);
  }

  public PsiReference findReferenceAt(int offset) {
    return getViewProvider().findReferenceAt(offset);
  }

  @NotNull
  public char[] textToCharArray() {
    return CharArrayUtil.fromSequenceStrict(getViewProvider().getContents());
  }
}
