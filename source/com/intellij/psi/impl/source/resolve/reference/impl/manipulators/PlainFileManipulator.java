package com.intellij.psi.impl.source.resolve.reference.impl.manipulators;

import com.intellij.psi.impl.source.resolve.reference.ElementManipulator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.util.IncorrectOperationException;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 09.12.2003
 * Time: 14:10:35
 * To change this template use Options | File Templates.
 */
public class PlainFileManipulator implements ElementManipulator {
  public PsiElement handleContentChange(PsiElement element, TextRange range, String newContent)
  throws IncorrectOperationException {
    final Document document = FileDocumentManager.getInstance().getDocument(((PsiFile)element).getVirtualFile());
    document.replaceString(range.getStartOffset(), range.getEndOffset(), newContent);
    PsiDocumentManager.getInstance(element.getProject()).commitDocument(document);

    return element;
  }
}
