
package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;

class EditorChangeAction implements UndoableAction {
  private DocumentEx myDocument;
  private int myOffset;
  private CharSequence myOldString;
  private CharSequence myNewString;
  private long myTimeStamp;
  private final Project myProject;

  public EditorChangeAction(DocumentEx document, int offset,
                            CharSequence oldString, CharSequence newString,
                            long oldTimeStamp, Project project) {
    myDocument = document;
    myOffset = offset;
    myOldString = oldString;
    if (myOldString == null)
      myOldString = "";
    myNewString = newString;
    if (myNewString == null)
      myNewString = "";
    myTimeStamp = oldTimeStamp;
    myProject = project;
  }

  public void undo() {
    exchangeStrings(myNewString, myOldString);
    myDocument.setModificationStamp(myTimeStamp);
    fileFileStatusChanged();
  }

  private void fileFileStatusChanged() {
    if (myProject == null) return;
    VirtualFile file = FileDocumentManager.getInstance().getFile(myDocument);
    if (file == null) return;
    FileStatusManager.getInstance(myProject).fileStatusChanged(file);
  }

  private void exchangeStrings(CharSequence newString, CharSequence oldString) {
    if (newString.length() > 0 && oldString.length() == 0){
      myDocument.deleteString(myOffset, myOffset + newString.length());
    }
    else if (oldString.length() > 0 && newString.length() == 0){
      myDocument.insertString(myOffset, oldString);
    }
    else if (oldString.length() > 0 && newString.length() > 0){
      myDocument.replaceString(myOffset, myOffset + newString.length(), oldString);
    }
  }

  public void redo() {
    exchangeStrings(myOldString, myNewString);
  }

  public DocumentReference[] getAffectedDocuments() {
    return new DocumentReference[]{DocumentReferenceByDocument.createDocumentReference(myDocument)};
  }

  public boolean isComplex() {
    return false;
  }
}

