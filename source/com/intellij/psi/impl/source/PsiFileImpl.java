package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
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
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

public abstract class PsiFileImpl extends NonSlaveRepositoryPsiElement implements PsiFileEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiFileImpl");

  private VirtualFile myFile;
  private String myName; // for myFile == null only

  private final IElementType myElementType;
  protected final IElementType myContentElementType;

  private long myModificationStamp;
  private boolean myModificationStampSticky = false;

  protected PsiFile myOriginalFile;
  private boolean myExplicitlySetAsPhysical;
  private boolean myExplicitlySetAsValid;

  protected PsiFileImpl(Project project, IElementType elementType, IElementType contentElementType, VirtualFile file) {
    super((PsiManagerImpl)PsiManager.getInstance(project), -2);
    myFile = file;
    myElementType = elementType;
    myContentElementType = contentElementType;
    myModificationStamp = file.getModificationStamp();
  }

  protected PsiFileImpl(Project project,
                        IElementType elementType,
                        IElementType contentElementType,
                        String name,
                        CharSequence text) {
    this(project, (FileElement)Factory.createCompositeElement(elementType), elementType, contentElementType, name);
    final FileElement parent = getTreeElement();
    char[] chars = CharArrayUtil.fromSequence(text);
    TreeElement contentElement = createContentLeafElement(chars, 0, text.length(), parent.getCharTable());
    TreeUtil.addChildren(parent, contentElement);
  }

  protected PsiFileImpl(Project project,
                        FileElement fileElement,
                        IElementType elementType,
                        IElementType contentElementType,
                        String name) {
    super((PsiManagerImpl)PsiManager.getInstance(project), fileElement);
    LOG.assertTrue(name != null);
    myName = name;
    myFile = null;
    myElementType = elementType;
    myContentElementType = contentElementType;
    myModificationStamp = LocalTimeCounter.currentTime();
  }

  public TreeElement createContentLeafElement(final char[] text, final int startOffset, final int endOffset, final CharTable table) {
    return Factory.createLeafElement(myContentElementType, text, startOffset, endOffset, -1, table);
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
    if (isRepositoryIdInitialized()) {
      super.prepareToRepositoryIdInvalidation();
    }
  }

  protected boolean isKeepTreeElementByHardReference() {
    return myFile == null || myExplicitlySetAsPhysical;
  }

  private ASTNode _getTreeElement() {
    return super.getTreeElement();
  }

  public VirtualFile getVirtualFile() {
    return myFile;
  }

  public boolean isValid() {
    if (myFile == null || myExplicitlySetAsValid) return true; // "dummy" file
    return myFile.isValid();
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
      final CharSequence docText = document.getCharsSequence();

      treeElement = createFileElement(docText);

      treeElement.setDocument(document);
      setTreeElement(treeElement);
      treeElement.setPsiElement(this);
      ((PsiDocumentManagerImpl)PsiDocumentManager.getInstance(myManager.getProject())).contentsLoaded(this);
      if (LOG.isDebugEnabled()) {
        LOG.debug("Loaded text for file " + myFile.getPresentableUrl());
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
    return myModificationStamp;
  }

  public void setModificationStamp(long modificationStamp) {
    myModificationStamp = modificationStamp;
  }

  public void setModificationStampSticky(boolean timeStampSticky) {
    myModificationStampSticky = timeStampSticky;
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

    subtreeChanged();
    return PsiFileImplUtil.setName(this, name);
  }

  public void checkSetName(String name) throws IncorrectOperationException {
    if (myFile == null) return;
    PsiFileImplUtil.checkSetName(this, name);
  }

  public boolean isWritable() {
    return myFile == null || myFile.isWritable();
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

  public void setOriginalFile(final PsiFile originalFile) {
    if(originalFile.getOriginalFile() != null) myOriginalFile = originalFile.getOriginalFile();
    else myOriginalFile = originalFile;
  }

  public boolean canContainJavaCode() {
    return false;
  }

  @NotNull
  public PsiFile[] getPsiRoots() {
    return new PsiFile[]{this};
  }

  @NotNull
  public PsiFile createPseudoPhysicalCopy() {
    PsiFileImpl copy = (PsiFileImpl)copy();
    copy.setIsPhysicalExplicitly(true); //?
    return copy;
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

  @NotNull
  public Language getLanguage() {
    final FileType fileType = getFileType();
    return fileType instanceof LanguageFileType ? ((LanguageFileType)fileType).getLanguage() : Language.ANY;
  }
}
