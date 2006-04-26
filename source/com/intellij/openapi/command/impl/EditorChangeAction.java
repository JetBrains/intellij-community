
package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceByDocument;
import com.intellij.openapi.command.undo.DocumentReferenceByVirtualFile;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;

class EditorChangeAction implements UndoableAction {
  private final DocumentEx myDocument; // DocumentEx or WeakReference<DocumentEx> or null
  private final VirtualFile myDocumentFile;
  private int myOffset;
  private CharSequence myOldString;
  private CharSequence myNewString;
  private long myTimeStamp;

  public EditorChangeAction(DocumentEx document, int offset,
                            CharSequence oldString, CharSequence newString,
                            long oldTimeStamp) {
    myDocumentFile = FileDocumentManager.getInstance().getFile(document);
    if (myDocumentFile != null) {
      myDocument = null;
    }
    else {
      myDocument = document;
    }

    myOffset = offset;
    myOldString = oldString;
    if (myOldString == null) {
      myOldString = "";
    }
    myNewString = newString;
    if (myNewString == null) {
      myNewString = "";
    }
    myTimeStamp = oldTimeStamp;
  }

  public void undo() {
    exchangeStrings(myNewString, myOldString);
    getDocument().setModificationStamp(myTimeStamp);
    fileFileStatusChanged();
  }

  private void fileFileStatusChanged() {
    VirtualFile file = myDocumentFile != null ? myDocumentFile : FileDocumentManager.getInstance().getFile(getDocument());
    if (file == null || file instanceof LightVirtualFile) return;

    final Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : projects) {
      FileStatusManager.getInstance(project).fileStatusChanged(file);
    }
  }

  private void exchangeStrings(CharSequence newString, CharSequence oldString) {
    if (newString.length() > 0 && oldString.length() == 0){
      getDocument().deleteString(myOffset, myOffset + newString.length());
    }
    else if (oldString.length() > 0 && newString.length() == 0){
      getDocument().insertString(myOffset, oldString);
    }
    else if (oldString.length() > 0 && newString.length() > 0){
      getDocument().replaceString(myOffset, myOffset + newString.length(), oldString);
    }
  }

  public void redo() {
    exchangeStrings(myOldString, myNewString);
  }

  public DocumentReference[] getAffectedDocuments() {
    if (myDocumentFile instanceof LightVirtualFile) {
      return DocumentReference.EMPTY_ARRAY;
    }

    final DocumentReference ref = myDocument != null
                                  ? DocumentReferenceByDocument.createDocumentReference(myDocument)
                                  : new DocumentReferenceByVirtualFile(myDocumentFile);
    return new DocumentReference[]{ref};
  }

  public boolean isComplex() {
    return false;
  }

  public DocumentEx getDocument() {
    if (myDocument != null) return myDocument;
    return (DocumentEx)FileDocumentManager.getInstance().getDocument(myDocumentFile);
  }
}

