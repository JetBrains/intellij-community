/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.psi;

import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.ide.highlighter.custom.impl.CustomFileType;
import com.intellij.lang.Language;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.StdLanguages;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.dummy.DummyFileSystem;
import com.intellij.openapi.vfs.impl.local.VirtualFileImpl;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.compiled.ClsFileImpl;
import com.intellij.psi.impl.file.PsiBinaryFileImpl;
import com.intellij.psi.impl.file.impl.FileManagerImpl;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.PsiPlainTextFileImpl;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;

public class SingleRootFileViewProvider extends UserDataHolderBase implements FileViewProvider {
  private static final Logger LOG = Logger.getInstance("#" + SingleRootFileViewProvider.class.getCanonicalName());
  private final PsiManager myManager;
  private VirtualFile myFile;
  private final boolean myEventSystemEnabled;
  private boolean myPhysical;
  private PsiFile myPsiFile = null;
  private Content myContent;
  private WeakReference<Document> myDocument;
  private Language myBaseLanguage;

  public SingleRootFileViewProvider(PsiManager manager, VirtualFile file) {
    this(manager, file, true);
  }

  public SingleRootFileViewProvider(PsiManager manager, final VirtualFile virtualFile, final boolean physical) {
    myManager = manager;
    myFile = virtualFile;
    myEventSystemEnabled = physical;
    myBaseLanguage = calcBaseLanguage(virtualFile);
    setContent(new VirtualFileContent());
    calcPhysical();
  }

  public Language getBaseLanguage() {
    return myBaseLanguage;
  }

  private static Language calcBaseLanguage(VirtualFile file) {
    final FileType fileType = file.getFileType();
    if (fileType instanceof LanguageFileType) {
      final LanguageFileType languageFileType = (LanguageFileType)fileType;
      return languageFileType.getLanguage();
    }
    else if (fileType instanceof CustomFileType ||
             fileType == StdFileTypes.GUI_DESIGNER_FORM ||
             fileType == StdFileTypes.IDEA_MODULE ||
             fileType == StdFileTypes.IDEA_PROJECT ||
             fileType == StdFileTypes.IDEA_WORKSPACE) {
      return StdLanguages.TEXT;
    }
    return Language.ANY;
  }

  public Set<Language> getRelevantLanguages() {
    return Collections.singleton(getBaseLanguage());
  }

  public Set<Language> getPrimaryLanguages() {
    return getRelevantLanguages();
  }

  public final PsiFile getPsi(Language target) {
    synchronized (PsiLock.LOCK) {
      if (!isPhysical()) {
        ((PsiManagerImpl)myManager).getFileManager().setViewProvider(getVirtualFile(), this);
      }
      return getPsiInner(target);
    }
  }

  @Nullable
  protected PsiFile getPsiInner(final Language target) {
    if (target != getBaseLanguage()) {
      return null;
    }
    synchronized (PsiLock.LOCK) {
      return myPsiFile != null ? myPsiFile : (myPsiFile = createFile());
    }
  }

  boolean myPostProcessReformafingStatus = false;

  public void beforeContentsSynchronized() {
  }

  public void contentsSynchronized() {
    unsetPsiContent();
  }

  private void unsetPsiContent() {
    if (!(myContent instanceof PsiFileContent)) return;
    final Document cachedDocument = getCachedDocument();
    if (cachedDocument != null) {
      setContent(new DocumentContent());
    }
    else {
      setContent(new VirtualFileContent());
    }
  }

  public void beforeDocumentChanged() {
    final PostprocessReformattingAspect component = myManager.getProject().getComponent(PostprocessReformattingAspect.class);
    if (component.isViewProviderLocked(this)) throw new RuntimeException("Document is locked by write PSI operations");
    component.doPostponedFormatting();
    final PsiFileImpl psiFile = (PsiFileImpl)getCachedPsi(getBaseLanguage());
    if (psiFile != null && psiFile.isContentsLoaded() && getContent()instanceof DocumentContent) {
      setContent(new PsiFileContent(psiFile, getModificationStamp()));
    }
  }

  public void rootChanged(PsiFile psiFile) {
    if (((PsiFileImpl)psiFile).isContentsLoaded()) {
      setContent(new PsiFileContent((PsiFileImpl)psiFile, LocalTimeCounter.currentTime()));
    }
  }

  public boolean isEventSystemEnabled() {
    return myEventSystemEnabled;
  }

  public boolean isPhysical() {
    return myPhysical;
  }

  private void calcPhysical() {
    myPhysical = !(getVirtualFile() instanceof LightVirtualFile) && !(getVirtualFile().getFileSystem() instanceof DummyFileSystem) &&
                 isEventSystemEnabled();
  }

  public long getModificationStamp() {
    return getContent().getModificationStamp();
  }


  public PsiFile getCachedPsi(Language target) {
    synchronized (PsiLock.LOCK) {
      return myPsiFile;
    }
  }

  public FileElement[] getKnownTreeRoots() {
    synchronized (PsiLock.LOCK) {
      if (myPsiFile == null || ((PsiFileImpl)myPsiFile).getTreeElement() == null) return new FileElement[0];
      return new FileElement[]{(FileElement)myPsiFile.getNode()};
    }
  }

  protected PsiFile createFile() {
    final VirtualFile vFile = getVirtualFile();

    try {
      if (vFile.isDirectory()) return null;
      final FileTypeManager fileTypeManager = FileTypeManager.getInstance();
      final String name = getVirtualFile().getName();
      if (fileTypeManager.isFileIgnored(name)) return null; // cannot use ProjectFileIndex because of "name"!

      final Project project = myManager.getProject();
      final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();

      if (isPhysical()) { // check directories consistency
        final VirtualFile parent = vFile.getParent();
        if (parent == null) return null;
        final PsiDirectory psiDir = getManager().findDirectory(parent);
        if (psiDir == null) return null;
      }

      FileType fileType = getRealFileType();

      if (fileType instanceof LanguageFileType) {
        final Language language = ((LanguageFileType)fileType).getLanguage();
        if (language == StdLanguages.JAVA || vFile == null || !isTooLarge(vFile)) {
          final PsiFile psiFile = createFile(language);
          if (psiFile != null) return psiFile;
        }
      }

      if (fileType instanceof JavaClassFileType) {
        if (projectFileIndex.isInLibraryClasses(vFile)) {
          // skip inners & anonymous
          int dotIndex = name.lastIndexOf('.');
          if (dotIndex < 0) dotIndex = name.length();
          int index = name.lastIndexOf('$', dotIndex);
          if (index >= 0) return null;

          return new ClsFileImpl((PsiManagerImpl)PsiManager.getInstance(project), this);
        }
        return null;
      }

      if (fileType.isBinary()) {
        return new PsiBinaryFileImpl((PsiManagerImpl)getManager(), this);
      }
      return new PsiPlainTextFileImpl(this);
    }
    catch (Throwable e) {
      LOG.error(e);
      return null;
    }
  }

  private FileType getRealFileType() {
    FileType fileType = getVirtualFile().getFileType();
    if (!isPhysical()) return fileType;
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(getManager().getProject()).getFileIndex();
    if (fileType == StdFileTypes.JAVA && !projectFileIndex.isInSource(getVirtualFile())) fileType = StdFileTypes.PLAIN_TEXT;
    return fileType;
  }

  private static boolean isTooLarge(final VirtualFile vFile) {
    return FileManagerImpl.MAX_INTELLISENSE_FILESIZE != -1 && fileSize(vFile) > FileManagerImpl.MAX_INTELLISENSE_FILESIZE;
  }

  private static long fileSize(final VirtualFile vFile) {
    if (vFile instanceof VirtualFileImpl) {
      return ((VirtualFileImpl)vFile).getPhysicalFileLength();
    }
    return vFile.getLength();
  }

  @Nullable
  protected PsiFile createFile(Language lang) {
    if (lang != getBaseLanguage()) return null;
    final ParserDefinition parserDefinition = lang.getParserDefinition();
    if (parserDefinition != null) {
      try {
        return parserDefinition.createFile(this);
      }
      catch(AbstractMethodError ame) {
        // support 5.x version of the API
        try {
          return createFileIridaAPI(lang);
        }
        catch(Exception ex) {
          throw ame;
        }
      }
    }
    return null;
  }

  private PsiFile createFileIridaAPI(final Language lang) throws Exception {
    ParserDefinition parserDefinition = lang.getParserDefinition();
    assert parserDefinition != null;
    //noinspection HardCodedStringLiteral
    Method m = parserDefinition.getClass().getMethod("createFile", Project.class, VirtualFile.class);
    PsiFile file = (PsiFile)m.invoke(parserDefinition, myManager.getProject(), myFile);
    if (file instanceof PsiFileImpl) {
      ((PsiFileImpl)file).setViewProvider(this);
      return file;
    }
    throw new IllegalStateException("Original version of ParserDefinition is supported only for PsiFileImpl implementations");
  }

  public PsiManager getManager() {
    return myManager;
  }

  @NotNull
  public CharSequence getContents() {
    synchronized (PsiLock.LOCK) {
      return getContent().getText();
    }
  }

  @NotNull
  public VirtualFile getVirtualFile() {
    return myFile;
  }

  public void setVirtualFile(final VirtualFile file) {
    myFile = file;
    myDocument.clear();
    myPsiFile = null;
    myBaseLanguage = calcBaseLanguage(file);
    calcPhysical();
  }

  private Document getCachedDocument() {
    final Document document = myDocument != null ? myDocument.get() : null;
    if (document != null) return document;
    return FileDocumentManager.getInstance().getCachedDocument(getVirtualFile());
  }

  public Document getDocument() {
    Document document = myDocument != null ? myDocument.get() : null;
    if (document == null/* TODO[ik] make this change && isEventSystemEnabled()*/) {
      document = FileDocumentManager.getInstance().getDocument(getVirtualFile());
      myDocument = new WeakReference<Document>(document);
    }
    if (document != null && getContent()instanceof VirtualFileContent) {
      setContent(new DocumentContent());
    }
    return document;
  }

  public FileViewProvider clone() {
    return new SingleRootFileViewProvider(getManager(), new LightVirtualFile(getVirtualFile().getName(), getRealFileType(), getContents(),
                                                                             getModificationStamp()), false);
  }

  public PsiReference findReferenceAt(final int offset) {
    final PsiFileImpl psiFile = (PsiFileImpl)getPsi(getBaseLanguage());
    return findReferenceAt(psiFile, offset);
  }

  public PsiElement findElementAt(final int offset, final Language language) {
    final PsiFile psiFile = getPsi(language);
    return psiFile != null ? findElementAt(psiFile, offset) : null;
  }

  @Nullable
  public PsiReference findReferenceAt(final int offset, final Language language) {
    final PsiFile psiFile = getPsi(language);
    return psiFile != null ? findReferenceAt(psiFile, offset) : null;
  }

  public Lexer createLexer(final Language language) {
    if (language != getBaseLanguage() && language != Language.ANY) return null;
    final ParserDefinition parserDefinition = language.getParserDefinition();
    if (parserDefinition == null) return ((PsiFileImpl)getPsi(getBaseLanguage())).createLexer();
    return parserDefinition.createLexer(getManager().getProject());
  }

  public boolean isLockedByPsiOperations() {
    final PostprocessReformattingAspect component = myManager.getProject().getComponent(PostprocessReformattingAspect.class);
    return component.isViewProviderLocked(this);
  }

  @Nullable
  protected static PsiReference findReferenceAt(final PsiFile psiFile, final int offset) {
    int offsetInElement = offset;
    PsiElement child = psiFile.getFirstChild();
    while (child != null) {
      final int length = child.getTextLength();
      if (length <= offsetInElement) {
        offsetInElement -= length;
        child = child.getNextSibling();
        continue;
      }
      return child.findReferenceAt(offsetInElement);
    }
    return null;
  }

  public PsiElement findElementAt(final int offset) {
    return findElementAt(getPsi(getBaseLanguage()), offset);
  }


  public PsiElement findElementAt(int offset, Class<? extends Language> lang) {
    if (!lang.isAssignableFrom(getBaseLanguage().getClass())) return null;
    return findElementAt(offset);
  }

  @Nullable
  protected static PsiElement findElementAt(final PsiElement psiFile, final int offset) {
    int offsetInElement = offset;
    PsiElement child = psiFile.getFirstChild();
    while (child != null) {
      final int length = child.getTextLength();
      if (length <= offsetInElement) {
        offsetInElement -= length;
        child = child.getNextSibling();
        continue;
      }
      return child.findElementAt(offsetInElement);
    }
    return null;
  }

  public void forceCachedPsi(final PsiFile psiCodeFragment) {
    synchronized (PsiLock.LOCK) {
      myPsiFile = psiCodeFragment;
      ((PsiManagerImpl)myManager).getFileManager().setViewProvider(getVirtualFile(), this);
    }
  }

  private Content getContent() {
    return myContent;
  }

  private void setContent(final Content content) {
    myContent = content;
  }

  private static interface Content {
    CharSequence getText();

    long getModificationStamp();
  }

  private class VirtualFileContent implements Content {
    public CharSequence getText() {
      final Document document = getDocument();
      if (document == null) {
        return LoadTextUtil.loadText(getVirtualFile());
      }
      else {
        return document.getCharsSequence();
      }
    }

    public long getModificationStamp() {
      return getVirtualFile().getModificationStamp();
    }
  }

  private class DocumentContent implements Content {
    public CharSequence getText() {
      final Document document = getDocument();
      assert document != null;
      return document.getCharsSequence().subSequence(0, document.getTextLength());
    }

    public long getModificationStamp() {
      Document document = myDocument != null ? myDocument.get() : null;
      if (document != null) return document.getModificationStamp();
      return myFile.getModificationStamp();
    }
  }

  private class PsiFileContent implements Content {
    private PsiFileImpl myFile;
    private CharSequence myContent = null;
    private long myModificationStamp;

    public PsiFileContent(final PsiFileImpl file, final long modificationStamp) {
      myFile = file;
      myModificationStamp = modificationStamp;
    }

    public CharSequence getText() {
      if (!myFile.isContentsLoaded()) {
        unsetPsiContent();
        return getContents();
      }
      if (myContent != null) return myContent;
      return myContent = myFile.calcTreeElement().getText();
    }

    public long getModificationStamp() {
      if (!myFile.isContentsLoaded()) {
        unsetPsiContent();
        return SingleRootFileViewProvider.this.getModificationStamp();
      }
      return myModificationStamp;
    }
  }
}
