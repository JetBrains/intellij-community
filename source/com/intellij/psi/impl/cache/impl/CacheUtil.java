package com.intellij.psi.impl.cache.impl;

import com.intellij.ExtensionPoints;
import com.intellij.ide.startup.FileContent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiElementFactoryImpl;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.compiled.ClsFileImpl;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.search.IndexPatternProvider;

import java.io.IOException;

public class CacheUtil {
  public static Key<Boolean> CACHE_COPY_KEY = new Key<Boolean>("CACHE_COPY_KEY");

  private CacheUtil() {
  }

  public static PsiFile createFileCopy(PsiFile psiFile) {
    return createFileCopy(null, psiFile);
  }

  public static PsiFile createFileCopy(FileContent content, PsiFile psiFile) {
    VirtualFile vFile = psiFile.getVirtualFile();
    if (vFile == null) return psiFile; // It's already a copy created via PsiManager.getFile(FileContent). Usually happens on initial startup.

    PsiFile fileCopy;
    if (psiFile instanceof ClsFileImpl) {
      ClsFileImpl implFile = (ClsFileImpl)psiFile;
      if (implFile.isContentsLoaded()) {
        fileCopy = implFile;
      }
      else {
        fileCopy = new ClsFileImpl((PsiManagerImpl)psiFile.getManager(), psiFile.getViewProvider());
        ((ClsFileImpl)fileCopy).setRepositoryId(-1);
      }
    }
    else if (psiFile instanceof PsiFileImpl) {
      PsiFileImpl implFile = (PsiFileImpl)psiFile;
      if (implFile.isContentsLoaded()) {
        fileCopy = implFile;
      }
      else {
        CharSequence text;
        if (content == null) {
          Document document = FileDocumentManager.getInstance().getDocument(vFile);
          text = document.getCharsSequence();
        }
        else {
          text = getContentText(content);
        }

        FileType fileType = psiFile.getFileType();
        /* No longer necessary?
        if (psiFile instanceof PsiPlainTextFile && (fileType == StdFileTypes.JAVA || fileType == StdFileTypes.ASPECT)) { // fixes problem with java&aspect files outside sourcepath
          fileType = StdFileTypes.PLAIN_TEXT;
        }
        */
        PsiElementFactoryImpl factory = (PsiElementFactoryImpl)psiFile.getManager().getElementFactory();
        final String name = psiFile.getName();
        assert name != null;
        fileCopy = factory.createFileFromText(name, fileType, text, psiFile.getModificationStamp(), false, false);
        fileCopy.putUserData(CACHE_COPY_KEY, Boolean.TRUE);
        ((PsiFileImpl)fileCopy).setOriginalFile(psiFile);
      }
    }
    else {
      fileCopy = psiFile;
    }

    return fileCopy;
  }

  private static final Key<CharSequence> CONTENT_KEY = new Key<CharSequence>("CONTENT_KEY");

  public static CharSequence getContentText(final FileContent content) {
    final Document doc = FileDocumentManager.getInstance().getCachedDocument(content.getVirtualFile());
    if (doc != null) return doc.getCharsSequence();

    CharSequence cached = content.getUserData(CONTENT_KEY);
    if (cached != null) return cached;
    try {
      cached = LoadTextUtil.getTextByBinaryPresentation(content.getBytes(), content.getVirtualFile(), false);
      content.putUserData(CONTENT_KEY, cached);
      return cached;
    }
    catch (IOException e) {
      return "";
    }
  }

  public static IndexPatternProvider[] getIndexPatternProviders() {
    return (IndexPatternProvider[]) Extensions.getRootArea().getExtensionPoint(ExtensionPoints.INDEX_PATTERN_PROVIDER).getExtensions();
  }
}
