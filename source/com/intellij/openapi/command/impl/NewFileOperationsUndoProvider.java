package com.intellij.openapi.command.impl;

import com.intellij.localvcs.integration.Checkpoint;
import com.intellij.localvcs.integration.LocalHistory;
import com.intellij.openapi.command.undo.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.*;

class NewFileOperationsUndoProvider extends AbstractFileOperationsUndoProvider {
  private Key<Boolean> DELETE_WAS_UNDOABLE = new Key<Boolean>("DeleteWasUndoable");

  private Project myProject;
  private UndoManagerImpl myUndoManager;
  private boolean myIsInsideCommand;

  public NewFileOperationsUndoProvider(UndoManagerImpl m, Project p) {
    myUndoManager = m;
    myProject = p;

    if (myProject == null) return;
    getFileManager().addVirtualFileListener(this);
  }

  @Override
  public void dispose() {
    if (myProject == null) return;
    getFileManager().removeVirtualFileListener(this);
  }

  private VirtualFileManager getFileManager() {
    return VirtualFileManager.getInstance();
  }

  @Override
  public void commandStarted(Project p) {
    if (myProject != p) return;
    myIsInsideCommand = true;
  }

  @Override
  public void commandFinished(Project p) {
    if (myProject != p) return;
    myIsInsideCommand = false;
  }

  public void fileCreated(VirtualFileEvent e) {
    processEvent(e);
  }

  public void propertyChanged(VirtualFilePropertyEvent e) {
    if (!e.getPropertyName().equals(VirtualFile.PROP_NAME)) return;
    processEvent(e);
  }

  public void fileMoved(VirtualFileMoveEvent e) {
    processEvent(e);
  }

  private void processEvent(VirtualFileEvent e) {
    if (shouldNotProcess(e)) return;
    if (isUndoable(e)) {
      createUndoableAction();
    }
    else {
      createNonUndoableAction(e);
    }
  }

  public void beforeContentsChange(VirtualFileEvent e) {
    if (shouldNotProcess(e)) return;
    if (isUndoable(e)) return;
    createNonUndoableAction(e);
  }

  public void beforeFileDeletion(VirtualFileEvent e) {
    if (shouldNotProcess(e)) return;
    if (isUndoable(e)) {
      e.getFile().putUserData(DELETE_WAS_UNDOABLE, true);
    }
    else {
      createNonUndoableAction(e);
    }
  }

  public void fileDeleted(VirtualFileEvent e) {
    VirtualFile f = e.getFile();

    if (f.getUserData(DELETE_WAS_UNDOABLE) != null) {
      createUndoableAction();
      f.putUserData(DELETE_WAS_UNDOABLE, null);
    }
  }

  private boolean shouldNotProcess(VirtualFileEvent e) {
    return myProject.isDisposed() || !LocalHistory.isUnderControl(myProject, e.getFile());
  }

  private boolean isUndoable(VirtualFileEvent e) {
    return !e.isFromRefresh();
  }

  private void createNonUndoableAction(VirtualFileEvent e) {
    VirtualFile f = e.getFile();

    DocumentReference newRef = new DocumentReferenceByVirtualFile(f);
    registerNonUndoableAction(newRef);

    DocumentReference oldRef = myUndoManager.findInvalidatedReferenceByUrl(f.getUrl());
    if (oldRef != null && !oldRef.equals(newRef)) {
      registerNonUndoableAction(oldRef);
    }
  }

  private void registerNonUndoableAction(final DocumentReference r) {
    if (myUndoManager.undoableActionsForDocumentAreEmpty(r)) return;

    myUndoManager.undoableActionPerformed(new NonUndoableAction() {
      public DocumentReference[] getAffectedDocuments() {
        return new DocumentReference[]{r};
      }

      public boolean isComplex() {
        return true;
      }
    });
  }

  private void createUndoableAction() {
    if (!myIsInsideCommand) return;
    myUndoManager.undoableActionPerformed(new MyUndoableAction());
  }

  private class MyUndoableAction implements UndoableAction {
    private Checkpoint myAfterActionCheckpoint;
    private Checkpoint myBeforeUndoCheckpoint;

    public MyUndoableAction() {
      myAfterActionCheckpoint = LocalHistory.putCheckpoint(myProject);
    }

    public void undo() throws UnexpectedUndoException {
      myBeforeUndoCheckpoint = LocalHistory.putCheckpoint(myProject);
      myAfterActionCheckpoint.revertToPreviousState();
    }

    public void redo() throws UnexpectedUndoException {
      myBeforeUndoCheckpoint.revertToThatState();
    }

    public DocumentReference[] getAffectedDocuments() {
      return DocumentReference.EMPTY_ARRAY;
    }

    public boolean isComplex() {
      return true;
    }
  }
}
