package com.intellij.psi.impl.cache.impl;

import com.intellij.ExtensionPoints;
import com.intellij.ide.startup.FileContent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiElementFactoryImpl;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.compiled.ClsFileImpl;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.search.IndexPatternProvider;
import com.intellij.util.ArrayUtil;
import com.intellij.util.text.CharArrayCharSequence;

import java.io.IOException;

public class CacheUtil {
  public static final int CACHE_THRESHOLD = 1024 * 1024 * 2; // Two megabytes

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
          try {
            text = LoadTextUtil.getTextByBinaryPresentation(content.getBytes(), vFile);
          }
          catch (IOException e) {
            text = new CharArrayCharSequence(ArrayUtil.EMPTY_CHAR_ARRAY);
          }
        }

        FileType fileType = psiFile.getFileType();
        /* No longer necessary?
        if (psiFile instanceof PsiPlainTextFile && (fileType == StdFileTypes.JAVA || fileType == StdFileTypes.ASPECT)) { // fixes problem with java&aspect files outside sourcepath
          fileType = StdFileTypes.PLAIN_TEXT;
        }
        */
        PsiElementFactoryImpl factory = (PsiElementFactoryImpl)psiFile.getManager().getElementFactory();
        fileCopy = factory.createFileFromText(psiFile.getName(), fileType, text, psiFile.getModificationStamp(), false, false);
        ((PsiFileImpl)fileCopy).setOriginalFile(psiFile);
      }
    }
    else {
      fileCopy = psiFile;
    }

    return fileCopy;
  }

  public static IndexPatternProvider[] getIndexPatternProviders() {
    return (IndexPatternProvider[]) Extensions.getRootArea().getExtensionPoint(ExtensionPoints.INDEX_PATTERN_PROVIDER).getExtensions();
  }
}
