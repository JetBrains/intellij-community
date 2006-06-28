package com.intellij.psi.impl.source.resolve.reference.impl.manipulators;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiPlainTextFile;
import com.intellij.psi.impl.source.resolve.reference.AbstractElementManipulator;
import com.intellij.util.IncorrectOperationException;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 09.12.2003
 * Time: 14:10:35
 * To change this template use Options | File Templates.
 */
public class PlainFileManipulator extends AbstractElementManipulator<PsiPlainTextFile> {
  public PsiPlainTextFile handleContentChange(PsiPlainTextFile file, TextRange range, String newContent)
  throws IncorrectOperationException {
    final Document document = FileDocumentManager.getInstance().getDocument(file.getVirtualFile());
    document.replaceString(range.getStartOffset(), range.getEndOffset(), newContent);
    PsiDocumentManager.getInstance(file.getProject()).commitDocument(document);

    return file;
  }
}
