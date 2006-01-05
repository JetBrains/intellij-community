/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.psi;

import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.lang.Language;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.dummy.DummyFileSystem;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.compiled.ClsFileImpl;
import com.intellij.psi.impl.file.PsiBinaryFileImpl;
import com.intellij.psi.impl.file.impl.FileManagerImpl;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.PsiPlainTextFileImpl;
import com.intellij.testFramework.MockVirtualFile;
import com.intellij.util.text.CharArrayCharSequence;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;
import java.lang.ref.WeakReference;

public class SingleRootFileViewProvider implements FileViewProvider {
  private static final Logger LOG = Logger.getInstance("#" + SingleRootFileViewProvider.class.getCanonicalName());
  private final PsiManager myManager;
  private final VirtualFile myFile;
  private PsiFile myPsiFile = null;
  private CharSequence myContents = null;
  private boolean myPhysical;
  private WeakReference<Document> myDocument;
  private long myModificationStamp;

  public SingleRootFileViewProvider(PsiManager manager, VirtualFile file) {
    this(manager, file, true);
  }

  public SingleRootFileViewProvider(PsiManager manager, final VirtualFile virtualFile, final boolean physical) {
    myManager = manager;
    myFile = virtualFile;
    myPhysical = physical;
    myModificationStamp = virtualFile.getModificationStamp();
  }

  public Language getBaseLanguage() {
    final FileType fileType = getVirtualFile().getFileType();
    if(fileType instanceof LanguageFileType){
      final LanguageFileType languageFileType = (LanguageFileType)fileType;
      return languageFileType.getLanguage();
    }
    return Language.ANY;
  }

  public Set<Language> getRelevantLanguages() {
    return Collections.singleton(getBaseLanguage());
  }

  public synchronized PsiFile getPsi(Language target) {
    if(!isPhysical())
      ((PsiManagerImpl)myManager).getFileManager().setViewProvider(getVirtualFile(), this);
    if (target != getBaseLanguage()) return null;
    return myPsiFile != null ? myPsiFile : (myPsiFile = createFile());
  }

  public void contentsChanged() {
    myContents = null;
  }

  public boolean isEventSystemEnabled() {
    return myPhysical;
  }

  public boolean isPhysical() {
    return !(getVirtualFile() instanceof MockVirtualFile) && !(getVirtualFile().getFileSystem() instanceof DummyFileSystem) && isEventSystemEnabled();
  }

  public long getModificationStamp() {
    final Document document = getCachedDocument();
    if (document != null) {
      if (!PsiDocumentManager.getInstance(getManager().getProject()).isUncommited(document)) {
        myModificationStamp = document.getModificationStamp();
      }
    }
    else {
      myModificationStamp = getVirtualFile().getModificationStamp();
    }
    return myModificationStamp;
  }

  public void rootChanged(PsiFile psiFile) {
  }

  public synchronized PsiFile getCachedPsi(Language target) {
    return myPsiFile;
  }

  protected PsiFile createFile() {
    final VirtualFile vFile = getVirtualFile();

    try {
      if (vFile.isDirectory()) return null;
      final FileTypeManager fileTypeManager = FileTypeManager.getInstance();
      final String name = getVirtualFile().getName();
      if (fileTypeManager.isFileIgnored(name)) return null; // cannot use ProjectFileIndex because of "name"!

      if(isPhysical()){ // check directories consistency
        final VirtualFile parent = vFile.getParent();
        if (parent == null) return null;
        final PsiDirectory psiDir = getManager().findDirectory(parent);
        if (psiDir == null) return null;
      }

      FileType fileType = getVirtualFile().getFileType();
      final Project project = myManager.getProject();
      if (fileType instanceof LanguageFileType) {
        final Language language = ((LanguageFileType)fileType).getLanguage();
        if (language == StdLanguages.JAVA || vFile == null || !isTooLarge(vFile)) {
          final ParserDefinition parserDefinition = language.getParserDefinition();
          if (parserDefinition != null) {
            return parserDefinition.createFile(this);
          }
        }
      }

      if (fileType instanceof JavaClassFileType) {
        ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
        if (fileIndex.isInLibraryClasses(vFile)) {
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

  private boolean isTooLarge(final VirtualFile vFile) {
    if (FileManagerImpl.MAX_INTELLISENSE_FILESIZE == -1) return false;
    return getFileLength(vFile) > FileManagerImpl.MAX_INTELLISENSE_FILESIZE;
  }

  private long getFileLength(final VirtualFile vFile) {
    return vFile.getLength();
  }

  public PsiManager getManager() {
    return myManager;
  }

  @NotNull
  public synchronized CharSequence getContents() {
    if(myModificationStamp != getModificationStamp()){
      contentsChanged();
    }
    if(myContents == null){
      final Document document = getDocument();
      final PsiFileImpl cachedPsi = (PsiFileImpl)getCachedPsi(getBaseLanguage());
      if (cachedPsi != null && cachedPsi.isContentsLoaded() && PsiDocumentManager.getInstance(myManager.getProject()).isUncommited(document)) {
        myContents = new CharArrayCharSequence(cachedPsi.textToCharArray());
      }
      else if (document != null) {
        return document.getCharsSequence();
      }
      else {
        throw new RuntimeException("There can't be character contents for binary files!");
      }
    }
    return myContents;
  }

  @NotNull
  public VirtualFile getVirtualFile() {
    return myFile;
  }

  private Document getCachedDocument() {
    final Document document = myDocument != null ? myDocument.get() : null;
    if (document != null) return document;
    return FileDocumentManager.getInstance().getCachedDocument(getVirtualFile());
  }

  public Document getDocument() {
    Document document = myDocument != null ? myDocument.get() : null;
    if(document == null) {
      document = FileDocumentManager.getInstance().getDocument(getVirtualFile());
      myDocument = new WeakReference<Document>(document);
    }
    return document;
  }

  public FileViewProvider clone(){
    final SingleRootFileViewProvider clone =
      new SingleRootFileViewProvider(getManager(),
                                     new MockVirtualFile(getVirtualFile().getName(),
                                                         getVirtualFile().getFileType(),
                                                         getContents(),
                                                         getModificationStamp()),
                                     false);
    return clone;
  }

  public synchronized void forceCachedPsi(final PsiFile psiCodeFragment) {
    myPsiFile = psiCodeFragment;
    ((PsiManagerImpl)myManager).getFileManager().setViewProvider(getVirtualFile(), this);
  }
}
