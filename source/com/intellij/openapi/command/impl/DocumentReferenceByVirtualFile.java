package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author max
 */
public class DocumentReferenceByVirtualFile extends DocumentReference {
  private VirtualFile myFile;
  private String myUrl = null;

  public DocumentReferenceByVirtualFile(VirtualFile file) {
    myFile = file;
  }

  public void beforeFileDeletion(VirtualFile file) {
    if (myFile != file) return;
    if (!myFile.isValid()) return;
    myUrl = file.getUrl();
  }

  protected String getUrl() {
    if (myFile.isValid())
      return myFile.getUrl();
    else
      return myUrl;
  }

  public VirtualFile getFile() {
    return myFile;
  }

  public Document getDocument() {
    return FileDocumentManager.getInstance().getDocument(myFile);
  }
}
