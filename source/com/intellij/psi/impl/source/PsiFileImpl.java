package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageDialect;
import com.intellij.lexer.Lexer;
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
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayCharSequence;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public abstract class PsiFileImpl extends NonSlaveRepositoryPsiElement implements PsiFileEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiFileImpl");

  private IElementType myElementType;
  protected IElementType myContentElementType;

  protected PsiFile myOriginalFile = null;
  private FileViewProvider myViewProvider;
  private static final Key<Document> HARD_REFERENCE_TO_DOCUMENT = new Key<Document>("HARD_REFERENCE_TO_DOCUMENT");

  protected PsiFileImpl(IElementType elementType, IElementType contentElementType, FileViewProvider provider) {
    this(provider);
    init(elementType, contentElementType);
  }

  protected PsiFileImpl( FileViewProvider provider ) {
    super((PsiManagerEx)provider.getManager(), provider.isPhysical() ? -2 : -1);
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

  @Deprecated
  public TreeElement createContentLeafElement(final char[] text, final int startOffset, final int endOffset, final CharTable table) {
    return createContentLeafElement(new CharArrayCharSequence(text),startOffset, endOffset, table);
  }

  public TreeElement createContentLeafElement(final CharSequence text, final int startOffset, final int endOffset, final CharTable table) {
    return Factory.createLeafElement(myContentElementType, text, startOffset, endOffset, table);
  }

  public long getRepositoryId() {
    long id = super.getRepositoryId();
    if (id != -2) return id;
    synchronized (PsiLock.LOCK) {
      id = super.getRepositoryId();
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
  }

  public boolean isDirectory() {
    return false;
  }

  public boolean isRepositoryIdInitialized() {
    return super.getRepositoryId() != -2;
  }

  public FileElement getTreeElement() {
    final FileElement noLockAttempt = (FileElement)_getTreeElement();
    if (noLockAttempt != null) return noLockAttempt;

    synchronized (PsiLock.LOCK) {
      return getTreeElementNoLock();
    }
  }

  public FileElement getTreeElementNoLock() {
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

  public boolean processChildren(final PsiElementProcessor<PsiFileSystemItem> processor) {
    return true;
  }

  public boolean isValid() {
    if (!getViewProvider().isPhysical()) return true; // "dummy" file
    final VirtualFile vFile = getViewProvider().getVirtualFile();
    return vFile.isValid() && isPsiUpToDate(vFile);
  }

  protected boolean isPsiUpToDate(VirtualFile vFile) {
    final FileViewProvider viewProvider = myManager.findViewProvider(vFile);

    return viewProvider.getPsi(viewProvider.getBaseLanguage()) == this;
  }

  public boolean isContentsLoaded() {
    return _getTreeElement() != null;
  }

  public FileElement loadTreeElement() {
    FileElement treeElement = (FileElement)_getTreeElement();
    if (treeElement != null) return treeElement;
    if (getViewProvider().isPhysical() && myManager.isAssertOnFileLoading(getViewProvider().getVirtualFile())) {
      LOG.error("Access to tree elements not allowed in tests." + getViewProvider().getVirtualFile().getPresentableUrl());
    }
    final FileViewProvider viewProvider = getViewProvider();
    // load document outside lock for better performance
    final Document document = viewProvider.isEventSystemEnabled() ? viewProvider.getDocument() : null;
    //synchronized (PsiLock.LOCK) {
      treeElement = createFileElement(viewProvider.getContents());
      if (document != null) {
        treeElement.putUserData(HARD_REFERENCE_TO_DOCUMENT, document);
      }
      setTreeElement(treeElement);
      treeElement.setPsiElement(this);
    //}

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

    TreeElement contentElement = createContentLeafElement(docText, 0, docText.length(), treeElement.getCharTable());
    TreeUtil.addChildren(treeElement, contentElement);
    return treeElement;
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

  @SuppressWarnings({"CloneDoesntDeclareCloneNotSupportedException", "CloneDoesntCallSuperClone"})
  protected PsiFileImpl clone() {
    FileViewProvider provider = getViewProvider().clone();
    final LanguageDialect dialect = getLanguageDialect();
    PsiFileImpl clone = (PsiFileImpl)provider.getPsi(getLanguage());
    if (clone == null && dialect != null) {
      clone = (PsiFileImpl)provider.getPsi(dialect);
    }

    copyCopyableDataTo(clone);
    
    if (getTreeElement() != null) {
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

    if (dialect != null) {
      clone.putUserData(PsiManagerImpl.LANGUAGE_DIALECT, dialect);
    }

    return clone;
  }

  @NotNull public String getName() {
    return getViewProvider().getVirtualFile().getName();
  }

  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
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

  public PsiDirectory getParent() {
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
    final FileViewProvider viewProvider = getViewProvider();
    final Set<Language> languages = viewProvider.getPrimaryLanguages();
    final PsiFile[] roots = new PsiFile[languages.size()];
    int i = 0;
    for (Language language : languages) {
      roots[i++] = viewProvider.getPsi(language);
    }
    return roots;
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

  @NotNull
  protected <T> T[] findChildrenByClass(Class<T> aClass) {
    List<T> result = new ArrayList<T>();
    for (PsiElement child : getChildren()) {
      if (aClass.isInstance(child)) result.add((T)child);
    }
    return result.toArray((T[]) Array.newInstance(aClass, result.size()));
  }

  @Nullable
  protected <T> T findChildByClass(Class<T> aClass) {
    for (PsiElement child : getChildren()) {
      if (aClass.isInstance(child)) return (T)child;
    }
    return null;
  }

  @NonNls
  @Nullable
  public LanguageDialect getLanguageDialect() {
    return getUserData(PsiManagerImpl.LANGUAGE_DIALECT);
  }
}
