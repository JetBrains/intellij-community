package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author max
 */
public class DocumentReferenceByDocument extends DocumentReference {
  private Document myDocument;

  private DocumentReferenceByDocument(Document document) {
    myDocument = document;
  }

  public VirtualFile getFile() {
    return FileDocumentManager.getInstance().getFile(myDocument);
  }

  public Document getDocument() {
    return myDocument;
  }

  protected String getUrl() {
    VirtualFile file = getFile();
    if (file == null) return null;
    return file.getUrl();
  }

  public void beforeFileDeletion(VirtualFile file) {
  }

  public static DocumentReference createDocumentReference(Document document) {
    final VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    if (file != null) return new DocumentReferenceByVirtualFile(file);
    return new DocumentReferenceByDocument(document);
  }
}
