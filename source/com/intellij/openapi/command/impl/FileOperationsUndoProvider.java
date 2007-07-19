package com.intellij.openapi.command.impl;

import com.intellij.history.Checkpoint;
import com.intellij.history.LocalHistory;
import com.intellij.openapi.command.undo.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class FileOperationsUndoProvider extends VirtualFileAdapter {
  private Key<Boolean> DELETE_WAS_UNDOABLE = new Key<Boolean>("DeletionWasUndoable");

  private Project myProject;
  private UndoManagerImpl myUndoManager;
  private boolean myIsInsideCommand;

  private List<MyUndoableAction> myCommandActions;

  public FileOperationsUndoProvider(UndoManagerImpl m, Project p) {
    myUndoManager = m;
    myProject = p;

    if (myProject == null) return;
    getFileManager().addVirtualFileListener(this);
  }

  public void dispose() {
    if (myProject == null) return;
    getFileManager().removeVirtualFileListener(this);
  }

  private VirtualFileManager getFileManager() {
    return VirtualFileManager.getInstance();
  }

  public void commandStarted(Project p) {
    if (myProject != p) return;
    myIsInsideCommand = true;
    myCommandActions = new ArrayList<MyUndoableAction>();
  }

  public void commandFinished(Project p) {
    if (myProject != p) return;
    myIsInsideCommand = false;
    if (myCommandActions.isEmpty()) return;
    myCommandActions.get(0).beFirstInCommand();
    myCommandActions.get(myCommandActions.size() - 1).beLastInCommand();
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
    if (nonUndoableDeletion(e)) return;
    if (isUndoable(e)) {
      e.getFile().putUserData(DELETE_WAS_UNDOABLE, true);
    }
    else {
      createNonUndoableDeletionAction(e);
    }
  }

  private boolean nonUndoableDeletion(VirtualFileEvent e) {
    return LocalHistory.hasUnavailableContent(myProject, e.getFile());
  }

  public void fileDeleted(VirtualFileEvent e) {
    VirtualFile f = e.getFile();

    if (f.getUserData(DELETE_WAS_UNDOABLE) != null) {
      createUndoableAction();
      f.putUserData(DELETE_WAS_UNDOABLE, null);
    }
  }

  private boolean shouldNotProcess(VirtualFileEvent e) {
    return isProjectClosed() || !LocalHistory.isUnderControl(myProject, e.getFile());
  }

  private boolean isProjectClosed() {
    return myProject.isDisposed();
  }

  private boolean isUndoable(VirtualFileEvent e) {
    return !e.isFromRefresh();
  }

  private void createNonUndoableAction(VirtualFileEvent e) {
    createNonUndoableAction(e.getFile(), false);
  }

  private void createNonUndoableDeletionAction(VirtualFileEvent e) {
    createNonUndoableAction(e.getFile(), true);
  }

  private void createNonUndoableAction(VirtualFile f, boolean isDeletion) {
    DocumentReference newRef = new DocumentReferenceByVirtualFile(f);
    if (isDeletion) newRef.beforeFileDeletion(f);
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
    MyUndoableAction a = new MyUndoableAction();
    myUndoManager.undoableActionPerformed(a);
    myCommandActions.add(a);
  }

  private class MyUndoableAction implements UndoableAction {
    private Checkpoint myAfterActionCheckpoint;
    private Checkpoint myBeforeUndoCheckpoint;
    private boolean myUseUndo;
    private boolean myUseRedo;

    public MyUndoableAction() {
      myAfterActionCheckpoint = LocalHistory.putCheckpoint(myProject);
    }

    public void beFirstInCommand() {
      myUseUndo = true;
    }

    public void beLastInCommand() {
      myUseRedo = true;
    }

    public void undo() throws UnexpectedUndoException {
      myBeforeUndoCheckpoint = LocalHistory.putCheckpoint(myProject);

      if (!myUseUndo) return;
      try {
        myAfterActionCheckpoint.revertToPreviousState();
      }
      catch (IOException e) {
        throw new UnexpectedUndoException(e.getMessage());
      }
    }

    public void redo() throws UnexpectedUndoException {
      if (!myUseRedo) return;
      try {
        myBeforeUndoCheckpoint.revertToThatState();
      }
      catch (IOException e) {
        throw new UnexpectedUndoException(e.getMessage());
      }
    }

    public DocumentReference[] getAffectedDocuments() {
      return DocumentReference.EMPTY_ARRAY;
    }

    public boolean isComplex() {
      return true;
    }
  }
}
