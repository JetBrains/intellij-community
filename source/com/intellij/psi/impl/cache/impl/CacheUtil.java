package com.intellij.psi.impl.cache.impl;

import com.intellij.ide.startup.FileContent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPlainTextFile;
import com.intellij.psi.impl.PsiElementFactoryImpl;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.compiled.ClsFileImpl;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.util.text.CharArrayCharSequence;
import com.intellij.util.text.CharArrayUtil;

import java.io.IOException;

public class CacheUtil {
  public static PsiFile createFileCopy(PsiFile psiFile) {
    return createFileCopy(null, psiFile);
  }

  public static PsiFile createFileCopy(FileContent content, PsiFile psiFile) {
    PsiFile fileCopy;

    VirtualFile vFile = psiFile.getVirtualFile();
    if (psiFile instanceof ClsFileImpl) {
      ClsFileImpl implFile = (ClsFileImpl)psiFile;
      if (implFile.isContentsLoaded()) {
        fileCopy = implFile;
      }
      else {
        fileCopy = new ClsFileImpl((PsiManagerImpl)psiFile.getManager(), vFile);
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
          text = ((DocumentEx)document).getCharsSequence();
        }
        else {
          try {
            text = LoadTextUtil.loadText(content.getBytes(), new String[1]);
          }
          catch (IOException e) {
            text = new CharArrayCharSequence(new char[0]);
          }
        }

        FileType fileType = psiFile.getFileType();
        if (psiFile instanceof PsiPlainTextFile && (fileType == StdFileTypes.JAVA || fileType == StdFileTypes.ASPECT)) { // fixes problem with java&aspect files outside sourcepath
          fileType = StdFileTypes.PLAIN_TEXT;
        }
        PsiElementFactoryImpl factory = (PsiElementFactoryImpl)psiFile.getManager().getElementFactory();
        char[] chars = CharArrayUtil.fromSequence(text);
        fileCopy = factory.createDummyFileFromText(fileType, chars, 0, text.length());
      }
      fileCopy.setModificationStamp(psiFile.getModificationStamp());
    }
    else {
      fileCopy = psiFile;
    }

    return fileCopy;
  }
}
