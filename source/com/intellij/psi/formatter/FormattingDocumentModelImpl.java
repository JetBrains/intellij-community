package com.intellij.psi.formatter;

import com.intellij.newCodeFormatting.FormattingDocumentModel;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiDocumentManager;

public class FormattingDocumentModelImpl implements FormattingDocumentModel{
  
  private final Document myDocument;
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.formatter.FormattingDocumentModelImpl");

  public FormattingDocumentModelImpl(final Document document) {
    myDocument = document;
  }

  public static FormattingDocumentModelImpl createOn(PsiFile file) {
    final Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
    if (document != null && document.getText().equals(file.getText())) {      
      return new FormattingDocumentModelImpl(document);
    }
    else {
      return new FormattingDocumentModelImpl(new DocumentImpl(file.getText()));
    }
    
  }

  public int getLineNumber(int offset) {
    if (offset > myDocument.getTextLength()) {
      LOG.assertTrue(false);
    }
    return myDocument.getLineNumber(offset);
  }

  public int getLineStartOffset(int line) {
    return myDocument.getLineStartOffset(line);
  }

  public CharSequence getText(final TextRange textRange) {
    return myDocument.getCharsSequence().subSequence(textRange.getStartOffset(), textRange.getEndOffset());
  }

  public int getTextLength() {
    return myDocument.getTextLength();
  }
}
