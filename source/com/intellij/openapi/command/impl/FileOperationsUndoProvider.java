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
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

class FileOperationsUndoProvider extends VirtualFileAdapter implements LocalVcsItemsLocker {
  private Key<CompositeUndoableAction> DELETE_UNDOABLE_ACTION_KEY = new Key<CompositeUndoableAction>("DeleteUndoableAction");

  private Project myProject;
  private UndoManagerImpl myUndoManager;
  private Collection<LvcsRevision> myLockedRevisions = new HashSet<LvcsRevision>();
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

  public void fileCreated(VirtualFileEvent e) {
    if (shouldNotProcess(e)) return;

    if (isUndoable(e)) {
      createActionPerformed(e, true); //??
    }
    else {
      createNonUndoableAction(e, true);
    }
  }

  public void beforeContentsChange(VirtualFileEvent e) {
    if (shouldNotProcess(e)) return;
    if (isUndoable(e)) return;

    createNonUndoableAction(e, false);
  }

  public void propertyChanged(VirtualFilePropertyEvent e) {
    if (shouldNotProcess(e)) return;
    if (!e.getPropertyName().equals(VirtualFile.PROP_NAME)) return;

    if (isUndoable(e)) {
      createActionPerformed(e, false);
    }
    else {
      createNonUndoableAction(e, true);
    }
  }

  public void fileMoved(VirtualFileMoveEvent e) {
    if (shouldNotProcess(e)) return;

    if (isUndoable(e)) {
      createActionPerformed(e, false);
    }
    else {
      createNonUndoableAction(e, true);
    }
  }

  public void beforeFileDeletion(VirtualFileEvent e) {
    if (shouldNotProcess(e)) return;

    if (isUndoable(e)) {
      CompositeUndoableAction a = startUndoableAction(e, false);
      e.getFile().putUserData(DELETE_UNDOABLE_ACTION_KEY, a);
    }
    else {
      createNonUndoableAction(e, true);
    }
  }

  public void fileDeleted(VirtualFileEvent e) {
    VirtualFile f = e.getFile();
    CompositeUndoableAction a = f.getUserData(DELETE_UNDOABLE_ACTION_KEY);
    if (a == null) return;

    a.finishAction();
    myUndoManager.undoableActionPerformed(a);

    f.putUserData(DELETE_UNDOABLE_ACTION_KEY, null);
  }

  private boolean shouldNotProcess(VirtualFileEvent e) {
    return !getLvcs().isAvailable() || !getLvcs().isUnderVcs(e.getFile());
  }

  private boolean isUndoable(VirtualFileEvent e) {
    return !e.isFromRefresh() && getLvcs().isUnderVcs(e.getFile());
  }

  private void createNonUndoableAction(VirtualFileEvent e, boolean isComplex) {
    VirtualFile f = e.getFile();

    DocumentReference newRef = new DocumentReferenceByVirtualFile(f);
    createNonUndoableAction(newRef, isComplex);

    DocumentReference oldRef = myUndoManager.findInvalidatedReferenceByUrl(f.getUrl());
    if (oldRef != null && !oldRef.equals(newRef)) {
      createNonUndoableAction(oldRef, isComplex);
    }
  }

  private void createNonUndoableAction(DocumentReference r, boolean isComplex) {
    if (myUndoManager.undoableActionsForDocumentAreEmpty(r)) return;
    registerNonUndoableAction(r, isComplex);
  }

  private void registerNonUndoableAction(final DocumentReference r, final boolean isComplex) {
    myUndoManager.undoableActionPerformed(new NonUndoableAction() {
      public DocumentReference[] getAffectedDocuments() {
        return new DocumentReference[]{r};
      }

      public boolean isComplex() {
        return isComplex;
      }
    });
  }

  private void createActionPerformed(VirtualFileEvent e, boolean isContentAffected) {
    UndoableAction a = startUndoableAction(e, isContentAffected);
    if (a == null) return;
    myUndoManager.undoableActionPerformed(a);
  }

  private CompositeUndoableAction startUndoableAction(VirtualFileEvent e, boolean isContentsAffected) {
    if (!myCommandStarted) return null;
    CompositeUndoableAction a = new CompositeUndoableAction();
    addActionForFileTo(a, e.getFile(), isContentsAffected);
    return a;
  }

  private void addActionForFileTo(CompositeUndoableAction compositeUndoableAction,
                                  final VirtualFile vFile,
                                  final boolean isContentsAffected) {
    final String filePath = vFile.getPath();
    final boolean isDirectory = vFile.isDirectory();
    final LvcsRevision afterActionPerformedRevision = getCurrentRevision(filePath, isDirectory);
    if (afterActionPerformedRevision == null) return;
    synchronized (myLockedRevisions) {
      myLockedRevisions.add(afterActionPerformedRevision);
    }
    LvcsBasedUndoableAction action =
      new LvcsBasedUndoableAction(vFile, filePath, isDirectory, afterActionPerformedRevision, isContentsAffected);
    compositeUndoableAction.addAction(action);

    VirtualFile[] children = vFile.getChildren();
    if (children == null) return;
    for (VirtualFile aChildren : children) {
      addActionForFileTo(compositeUndoableAction, aChildren, isContentsAffected);
    }

  }

  @Nullable
  private LvcsRevision getCurrentRevision(String filePath, boolean isDir) {
    LocalVcs vcs = getLvcs();
    LvcsObject lvcsFile = isDir ? vcs.findDirectory(filePath, true) : vcs.findFile(filePath, true);
    if (lvcsFile == null) return null;
    return lvcsFile.getRevision();
  }

  private LocalVcs getLvcs() {
    return LocalVcs.getInstance(myProject);
  }

  private List<LvcsChange> getChanges(LvcsRevision requestedRevision, boolean isDir) {
    List<LvcsChange> changes = getChanges(requestedRevision, getCurrentRevision(getUpToDatePathFor(requestedRevision), isDir));
    return filterOnlyGlobalChanges(changes);
  }

  private List<LvcsChange> filterOnlyGlobalChanges(List<LvcsChange> changes) {
    ArrayList<LvcsChange> result = new ArrayList<LvcsChange>();
    for (final LvcsChange change : changes) {
      if (change.getChangeType() != LvcsChange.CONTENT_CHANGED) result.add(change);
    }
    return result;
  }

  private String getUpToDatePathFor(LvcsRevision requestedRevision) {
    LvcsRevision current = requestedRevision;
    while (current.getNextRevision() != null) current = current.getNextRevision();
    return current.getAbsolutePath();
  }

  private List<LvcsChange> getChanges(LvcsRevision requestedRevision, LvcsRevision lastRevision) {
    if (lastRevision == null || requestedRevision == null) {
      return new ArrayList<LvcsChange>();
    }
    return createChangesFromRevisions(collectRevisionsFromTo(requestedRevision, lastRevision));
  }

  private List<LvcsChange> createChangesFromRevisions(ArrayList<LvcsRevision> revisions) {
    return LocalVcsChanges.getChanges(revisions);
  }

  private ArrayList<LvcsRevision> collectRevisionsFromTo(LvcsRevision requestedRevision, LvcsRevision lastRevision) {
    ArrayList<LvcsRevision> revisions = new ArrayList<LvcsRevision>();

    revisions.add(requestedRevision);

    LvcsRevision currentRevision = requestedRevision;

    while (!currentRevision.equals(lastRevision)) {
      currentRevision = currentRevision.getNextRevision();
      assert !currentRevision.isPurged();
      revisions.add(currentRevision);
    }
    return revisions;
  }

  public void commandStarted(Project project) {
    if (myProject == project) {
      myCommandStarted = true;
    }
  }

  public void commandFinished(Project project) {
    if (myProject == project) {
      myCommandStarted = false;
    }
  }

  private class LvcsBasedUndoableAction implements UndoableAction, Disposable {
    private LvcsRevision myBeforeUndoRevision;
    private LvcsRevision myAfterActionPerformedRevision;
    private boolean myIsContentsAffected;

    private final VirtualFile myFile;
    private final String myFilePath;
    private final boolean myDirectory;

    public LvcsBasedUndoableAction(VirtualFile file,
                                   String filePath,
                                   boolean directory,
                                   LvcsRevision afterActionPerformedRevision,
                                   boolean contentsAffected) {
      myFile = file;
      myFilePath = filePath;
      myDirectory = directory;
      myAfterActionPerformedRevision = afterActionPerformedRevision;
      myIsContentsAffected = contentsAffected;
    }

    public void dispose() {
      synchronized (myLockedRevisions) {
        if (myBeforeUndoRevision != null) myLockedRevisions.remove(myBeforeUndoRevision);
        if (myAfterActionPerformedRevision != null) myLockedRevisions.remove(myAfterActionPerformedRevision);
      }
    }

    public void undo() throws UnexpectedUndoException {
      myBeforeUndoRevision = getCurrentRevision(myFilePath, myDirectory);
      if (myBeforeUndoRevision == null) return;
      try {
        rollbackTo(myAfterActionPerformedRevision);
        myBeforeUndoRevision = myBeforeUndoRevision.getNextRevision();
        if (myBeforeUndoRevision != null) {
          synchronized (myLockedRevisions) {
            myLockedRevisions.add(myBeforeUndoRevision);
          }
        }
      }
      catch (final IOException e) {
        myBeforeUndoRevision = null;
        Application application = ApplicationManager.getApplication();
        if (!application.isUnitTestMode()) {
          Runnable showDialogAction = new Runnable() {
            public void run() {
              Messages.showErrorDialog(CommonBundle.message("cannot.undo.command.error.message", e.getLocalizedMessage()),
                                       CommonBundle.message("cannot.undo.dialog.title"));
            }
          };

          if (application.isDispatchThread()) {
            showDialogAction.run();
          }
          else {
            application.invokeLater(showDialogAction);
          }
        }
        else {
          throw new RuntimeException(e);
        }
      }
    }

    public void redo() throws UnexpectedUndoException {
      try {
        rollbackTo(myBeforeUndoRevision);
      }
      catch (IOException e) {
        if (!ApplicationManager.getApplication().isUnitTestMode()) {
          Messages.showErrorDialog(CommonBundle.message("cannot.redo.error.message", e.getLocalizedMessage()),
                                   CommonBundle.message("cannot.redo.dialog.title"));
        }
        else {
          throw new RuntimeException(e);
        }
      }
    }

    private void rollbackTo(final LvcsRevision currentRevision) throws IOException {
      if (currentRevision == null) return;
      LocalVcsChanges.rollback(getChanges(currentRevision, myDirectory));
    }

    public DocumentReference[] getAffectedDocuments() {
      return myIsContentsAffected ? new DocumentReference[]{new DocumentReferenceByVirtualFile(myFile)} : DocumentReference.EMPTY_ARRAY;
    }

    public boolean isComplex() {
      return true;
    }

    public void finishAction() {
      myAfterActionPerformedRevision = myAfterActionPerformedRevision.findLatestRevision();
    }
  }

  private static class CompositeUndoableAction implements UndoableAction, Disposable {
    private final List<LvcsBasedUndoableAction> myActions = new ArrayList<LvcsBasedUndoableAction>();

    public CompositeUndoableAction addAction(LvcsBasedUndoableAction action) {
      myActions.add(action);
      return this;
    }

    public void undo() throws UnexpectedUndoException {
      for (final LvcsBasedUndoableAction action : myActions) {
        action.undo();
      }
    }

    public void redo() throws UnexpectedUndoException {
      for (int i = myActions.size() - 1; i >= 0; i--) {
        UndoableAction undoableAction = myActions.get(i);
        undoableAction.redo();
      }
    }

    public DocumentReference[] getAffectedDocuments() {
      ArrayList<DocumentReference> result = new ArrayList<DocumentReference>();
      for (final LvcsBasedUndoableAction action : myActions) {
        result.addAll(Arrays.asList(action.getAffectedDocuments()));
      }
      return result.toArray(new DocumentReference[result.size()]);
    }

    public boolean isComplex() {
      return true;
    }

    public void dispose() {
      for (final LvcsBasedUndoableAction action : myActions) {
        action.dispose();
      }
    }

    public boolean isEmpty() {
      return myActions.isEmpty();
    }

    public void finishAction() {
      for (LvcsBasedUndoableAction a : myActions) {
        a.finishAction();
      }
    }
  }

  public boolean itemCanBePurged(LvcsRevision revision) {
    synchronized (myLockedRevisions) {
      return !myLockedRevisions.contains(revision);
    }
  }
}
