package com.intellij.openapi.command.impl;

import com.intellij.CommonBundle;
import com.intellij.localVcs.changes.LocalVcsChanges;
import com.intellij.localVcs.changes.LvcsChange;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.*;
import com.intellij.openapi.localVcs.LocalVcs;
import com.intellij.openapi.localVcs.LocalVcsItemsLocker;
import com.intellij.openapi.localVcs.LvcsObject;
import com.intellij.openapi.localVcs.LvcsRevision;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.*;

import java.io.IOException;
import java.util.*;

class FileOperationsUndoProvider extends VirtualFileAdapter implements LocalVcsItemsLocker {
  private Key<CompositeUndoableAction> DELETE_UNDOABLE_ACTION_KEY = new Key<CompositeUndoableAction>("DeleteUndoableAction");

  private Project myProject;
  private UndoManagerImpl myUndoManager;
  private Set<LvcsRevision> myLockedRevisions = Collections.synchronizedSet(new HashSet<LvcsRevision>());
  private boolean myCommandStarted;


  public FileOperationsUndoProvider(UndoManagerImpl m, Project p) {
    myUndoManager = m;
    myProject = p;

    if (myProject == null) return;

    getLvcs().getLocalVcsPurgingProvider().registerLocker(this);
    getFileManager().addVirtualFileListener(this);
  }

  public void dispose() {
    if (myProject == null) return;

    getFileManager().removeVirtualFileListener(this);
    getLvcs().getLocalVcsPurgingProvider().unregisterLocker(this);
  }

  private VirtualFileManager getFileManager() {
    return VirtualFileManager.getInstance();
  }

  public void commandStarted(Project p) {
    if (myProject != p) return;
    myCommandStarted = true;
  }

  public void commandFinished(Project p) {
    if (myProject != p) return;
    myCommandStarted = false;
  }

  public void fileCreated(VirtualFileEvent e) {
    if (shouldNotProcess(e)) return;

    if (isUndoable(e)) {
      createUndoableAction(e, true); //??
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

  public void propertyChanged(VirtualFilePropertyEvent e) {
    if (shouldNotProcess(e)) return;
    if (!e.getPropertyName().equals(VirtualFile.PROP_NAME)) return;

    if (isUndoable(e)) {
      createUndoableAction(e, false);
    }
    else {
      createNonUndoableAction(e);
    }
  }

  public void fileMoved(VirtualFileMoveEvent e) {
    if (shouldNotProcess(e)) return;

    if (isUndoable(e)) {
      createUndoableAction(e, false);
    }
    else {
      createNonUndoableAction(e);
    }
  }

  public void beforeFileDeletion(VirtualFileEvent e) {
    if (shouldNotProcess(e)) return;

    if (isUndoable(e)) {
      CompositeUndoableAction a = startUndoableAction(e, false);
      e.getFile().putUserData(DELETE_UNDOABLE_ACTION_KEY, a);
    }
    else {
      createNonUndoableAction(e);
    }
  }

  public void fileDeleted(VirtualFileEvent e) {
    VirtualFile f = e.getFile();
    CompositeUndoableAction a = f.getUserData(DELETE_UNDOABLE_ACTION_KEY);
    if (a == null) return;

    a.completeAction();
    myUndoManager.undoableActionPerformed(a);

    f.putUserData(DELETE_UNDOABLE_ACTION_KEY, null);
  }

  private boolean shouldNotProcess(VirtualFileEvent e) {
    return !getLvcs().isAvailable() || !getLvcs().isUnderVcs(e.getFile());
  }

  private boolean isUndoable(VirtualFileEvent e) {
    return !e.isFromRefresh() && getLvcs().isUnderVcs(e.getFile());
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

  private void createUndoableAction(VirtualFileEvent e, boolean isContentAffected) {
    UndoableAction a = startUndoableAction(e, isContentAffected);
    if (a == null) return;
    myUndoManager.undoableActionPerformed(a);
  }

  private CompositeUndoableAction startUndoableAction(VirtualFileEvent e, boolean isContentAffected) {
    if (!myCommandStarted) return null;
    CompositeUndoableAction a = new CompositeUndoableAction();
    buildCompositeUndoableAction(a, e.getFile(), isContentAffected);
    return a;
  }

  private void buildCompositeUndoableAction(CompositeUndoableAction a, VirtualFile f, boolean isContentAffected) {
    String filePath = f.getPath();
    boolean isDirectory = f.isDirectory();

    LvcsRevision revisionAfterAction = getCurrentRevision(filePath, isDirectory);
    if (revisionAfterAction == null) return;

    myLockedRevisions.add(revisionAfterAction);

    a.addAction(new LvcsBasedUndoableAction(f, filePath, isDirectory, revisionAfterAction, isContentAffected));

    if (isDirectory) {
      for (VirtualFile child : f.getChildren()) {
        buildCompositeUndoableAction(a, child, isContentAffected);
      }
    }
  }

  private LvcsRevision getCurrentRevision(String path, boolean isDir) {
    LocalVcs vcs = getLvcs();
    LvcsObject o = isDir ? vcs.findDirectory(path, true) : vcs.findFile(path, true);
    if (o == null) return null;
    return o.getRevision();
  }

  public boolean itemCanBePurged(LvcsRevision revision) {
    return !myLockedRevisions.contains(revision);
  }

  private LocalVcs getLvcs() {
    return LocalVcs.getInstance(myProject);
  }

  private class LvcsBasedUndoableAction implements UndoableAction, Disposable {
    private final VirtualFile myFile;
    private final String myFilePath;
    private boolean myIsContentAffected;

    private final boolean myDirectory;
    private LvcsRevision myRevisionAfterAction;
    private LvcsRevision myRevisionAfterUndo;


    public LvcsBasedUndoableAction(VirtualFile file,
                                   String filePath,
                                   boolean directory,
                                   LvcsRevision revisionAfterAction,
                                   boolean contentAffected) {
      myFile = file;
      myFilePath = filePath;
      myDirectory = directory;
      myRevisionAfterAction = revisionAfterAction;
      myIsContentAffected = contentAffected;
    }

    public void dispose() {
      if (myRevisionAfterUndo != null) myLockedRevisions.remove(myRevisionAfterUndo);
      if (myRevisionAfterAction != null) myLockedRevisions.remove(myRevisionAfterAction);
    }

    public void undo() throws UnexpectedUndoException {
      LvcsRevision beforeUndo = getCurrentRevision(myFilePath, myDirectory);
      if (beforeUndo == null) return; // ?

      if (!rollbackToBefore(myRevisionAfterAction)) return;

      myRevisionAfterUndo = beforeUndo.getNextRevision();
      if (myRevisionAfterUndo != null) {
        myLockedRevisions.add(myRevisionAfterUndo);
      }
    }

    public void redo() throws UnexpectedUndoException {
      if (myRevisionAfterUndo == null) return;
      rollbackToBefore(myRevisionAfterUndo);
      myRevisionAfterUndo = null;
    }

    private boolean rollbackToBefore(LvcsRevision r) {
      try {
        LocalVcsChanges.rollback(getChanges(r));
        return true;
      }
      catch (final IOException e) {
        Application app = ApplicationManager.getApplication();
        Runnable runnable = new Runnable() {
          public void run() {
            Messages.showErrorDialog(CommonBundle.message("cannot.undo.command.error.message", e.getLocalizedMessage()),
                                     CommonBundle.message("cannot.undo.dialog.title"));
          }
        };
        if (app.isDispatchThread()) {
          runnable.run();
        }
        else {
          app.invokeLater(runnable);
        }
        return false;
      }
    }

    private List<LvcsChange> getChanges(LvcsRevision from) {
      List<LvcsChange> cc = LocalVcsChanges.getChanges(collectRevisions(from));
      return selectGlobalChanges(cc);
    }

    private List<LvcsRevision> collectRevisions(LvcsRevision from) {
      List<LvcsRevision> result = new ArrayList<LvcsRevision>();
      result.add(from);

      LvcsRevision r = from;
      while (r.getNextRevision() != null) {
        r = r.getNextRevision();
        assert !r.isPurged();
        result.add(r);
      }

      return result;
    }

    private List<LvcsChange> selectGlobalChanges(List<LvcsChange> cc) {
      ArrayList<LvcsChange> result = new ArrayList<LvcsChange>();
      for (LvcsChange c : cc) {
        if (c.getChangeType() != LvcsChange.CONTENT_CHANGED) result.add(c);
      }
      return result;
    }

    public DocumentReference[] getAffectedDocuments() {
      if (!myIsContentAffected) return DocumentReference.EMPTY_ARRAY;
      return new DocumentReference[]{new DocumentReferenceByVirtualFile(myFile)};
    }

    public boolean isComplex() {
      return true;
    }

    public void finishAction() {
      myRevisionAfterAction = myRevisionAfterAction.findLatestRevision();
    }
  }

  private static class CompositeUndoableAction implements UndoableAction, Disposable {
    private List<LvcsBasedUndoableAction> myActions = new ArrayList<LvcsBasedUndoableAction>();

    public void dispose() {
      for (LvcsBasedUndoableAction a : myActions) {
        a.dispose();
      }
    }

    public void addAction(LvcsBasedUndoableAction a) {
      myActions.add(a);
    }

    public void completeAction() {
      for (LvcsBasedUndoableAction a : myActions) {
        a.finishAction();
      }
    }

    public void undo() throws UnexpectedUndoException {
      for (LvcsBasedUndoableAction a : myActions) {
        a.undo();
      }
    }

    public void redo() throws UnexpectedUndoException {
      for (int i = myActions.size() - 1; i >= 0; i--) {
        myActions.get(i).redo();
      }
    }

    public DocumentReference[] getAffectedDocuments() {
      List<DocumentReference> result = new ArrayList<DocumentReference>();
      for (UndoableAction a : myActions) {
        result.addAll(Arrays.asList(a.getAffectedDocuments()));
      }
      return result.toArray(new DocumentReference[0]);
    }

    public boolean isComplex() {
      return true;
    }
  }
}
