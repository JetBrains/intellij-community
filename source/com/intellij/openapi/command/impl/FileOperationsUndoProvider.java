package com.intellij.openapi.command.impl;

import com.intellij.CommonBundle;
import com.intellij.localVcs.changes.LocalVcsChanges;
import com.intellij.localVcs.changes.LvcsChange;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.*;
import com.intellij.openapi.diagnostic.Logger;
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

/**
 * author: lesya
 */

class FileOperationsUndoProvider implements VirtualFileListener, LocalVcsItemsLocker {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.command.impl.FileOperationsUndoProvider");
  private final UndoManagerImpl myUndoManager;
  private final Project myProject;
  private boolean myCommandStarted;
  private final Collection<LvcsRevision> myLockedRevisions = new HashSet<LvcsRevision>();
  private final Key<CompositeUndoableAction> DELETE_UNDOABLE_ACTION_KEY = new Key<CompositeUndoableAction>("DeleteUndoableAction");

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

    public void actionCompleted() {
      for (final LvcsBasedUndoableAction action : myActions) {
        action.actionCompleted();
      }
    }
  }

  public FileOperationsUndoProvider(UndoManager undoManager, Project project) {
    myUndoManager = (UndoManagerImpl)undoManager;
    myProject = project;
    if (myProject != null) {
      virtualFileManager().addVirtualFileListener(this);

      if (LocalVcs.getInstance(myProject) != null) {
        LocalVcs.getInstance(myProject).getLocalVcsPurgingProvider().registerLocker(this);
      }
    }
  }

  public void dispose() {
    if (myProject != null) {
      virtualFileManager().removeVirtualFileListener(this);

      if (LocalVcs.getInstance(myProject) != null) {
        LocalVcs.getInstance(myProject).getLocalVcsPurgingProvider().unregisterLocker(this);
      }
    }
  }


  private VirtualFileManager virtualFileManager() {
    return VirtualFileManager.getInstance();
  }

  public void propertyChanged(VirtualFilePropertyEvent event) {
    if (VirtualFile.PROP_NAME.equals(event.getPropertyName())) {
      if (shouldProcess(event)) {
        undoableActionPerformed(event);
      } else {
        createNonUndoableAction(event.getFile(),true);
      }
    }
  }

  public void contentsChanged(VirtualFileEvent event) {
  }

  public void fileCreated(VirtualFileEvent event) {
    if (!shouldProcess(event)) {
      createNonUndoableAction(event.getFile(), true);
    }
    else {
      undoableActionPerformed(event);
    }
  }

  private boolean shouldProcess(final VirtualFileEvent event) {
    return !event.isFromRefresh() && getLocalVcs().isUnderVcs(event.getFile());
  }

  public void fileDeleted(VirtualFileEvent event) {
    VirtualFile file = event.getFile();
    try {
      CompositeUndoableAction deleteUndoableAction = file.getUserData(DELETE_UNDOABLE_ACTION_KEY);
      if (deleteUndoableAction == null) return;
      deleteUndoableAction.actionCompleted();
      myUndoManager.undoableActionPerformed(deleteUndoableAction);
    }
    finally {
      file.putUserData(DELETE_UNDOABLE_ACTION_KEY, null);
    }
  }

  public void fileMoved(VirtualFileMoveEvent event) {
    if (shouldProcess(event)) {
      undoableActionPerformed(event);
    } else {
      createNonUndoableAction(event.getFile(), true);
    }

  }

  public void beforePropertyChange(VirtualFilePropertyEvent event) {
  }

  public void beforeContentsChange(VirtualFileEvent event) {
    if (!shouldProcess(event)) {
      createNonUndoableAction(event.getFile(), false);
    }
  }

  public void beforeFileDeletion(VirtualFileEvent event) {
    VirtualFile file = event.getFile();
    if (!shouldProcess(event)) {
      createNonUndoableAction(file, true);
    }
    else {
      CompositeUndoableAction undoableAction = createUndoableAction(file);
      if (!undoableAction.isEmpty()) {
        file.putUserData(DELETE_UNDOABLE_ACTION_KEY, undoableAction);
      }

    }
  }

  public void beforeFileMovement(VirtualFileMoveEvent event) {
  }

  private void createNonUndoableAction(final VirtualFile vFile, final boolean isComplex) {
    if (getLocalVcs() == null) {
      return;
    }

    if (!getLocalVcs().isUnderVcs(vFile)) {
      return;
    }

    final DocumentReference newRef = new DocumentReferenceByVirtualFile(vFile);
    
    if (!vFile.isDirectory() && vFile.getFileType().isBinary()) {
      myUndoManager.undoableActionPerformed(new NonUndoableAction() {
        public DocumentReference[] getAffectedDocuments() {
          return new DocumentReference[]{newRef};
        }

        public boolean isComplex() {
          return isComplex;
        }
      });

    }

    DocumentReference oldRef = myUndoManager.findInvalidatedReferenceByUrl(vFile.getUrl());

    createNonUndoableAction(vFile, newRef, isComplex);

    if ((oldRef != null) && !oldRef.equals(newRef)) {
      createNonUndoableAction(vFile, oldRef, isComplex);
    }

  }

  private void createNonUndoableAction(final VirtualFile vFile,
                                       final DocumentReference newRef,
                                       final boolean isComplex) {
    if (getLocalVcs() == null || !getLocalVcs().isUnderVcs(vFile) ||
        myUndoManager.undoableActionsForDocumentAreEmpty(newRef)) {
      return;
    }

    myUndoManager.undoableActionPerformed(new NonUndoableAction() {
      public DocumentReference[] getAffectedDocuments() {
        return new DocumentReference[]{newRef};
      }

      public boolean isComplex() {
        return isComplex;
      }
    });
  }


  private void undoableActionPerformed(final VirtualFile vFile) {
    CompositeUndoableAction compositeUndoableAction = createUndoableAction(vFile);
    if (!compositeUndoableAction.isEmpty()) {
      myUndoManager.undoableActionPerformed(compositeUndoableAction);
    }
  }

  private CompositeUndoableAction createUndoableAction(final VirtualFile vFile) {

    CompositeUndoableAction compositeUndoableAction = new CompositeUndoableAction();

    if (myCommandStarted && (getLocalVcs() != null) && getLocalVcs().isAvailable()) {
      addActionForFileTo(compositeUndoableAction, vFile);
    }
    return compositeUndoableAction;
  }

  private void addActionForFileTo(CompositeUndoableAction compositeUndoableAction, final VirtualFile vFile) {
    final String filePath = vFile.getPath();
    final boolean isDirectory = vFile.isDirectory();
    final LvcsRevision afterActionPerformedRevision = getCurrentRevision(filePath, isDirectory);
    if (afterActionPerformedRevision == null) return;
    synchronized (myLockedRevisions) {
      myLockedRevisions.add(afterActionPerformedRevision);
    }
    LvcsBasedUndoableAction action = new LvcsBasedUndoableAction(vFile, filePath, isDirectory, afterActionPerformedRevision);
    compositeUndoableAction.addAction(action);

    VirtualFile[] children = vFile.getChildren();
    if (children == null) return;
    for (VirtualFile aChildren : children) {
      addActionForFileTo(compositeUndoableAction, aChildren);
    }

  }

  @Nullable
  private LvcsRevision getCurrentRevision(String filePath, boolean isDir) {
    LocalVcs vcs = getLocalVcs();
    LvcsObject lvcsFile = isDir ? vcs.findDirectory(filePath, true) : vcs.findFile(filePath, true);
    if (lvcsFile == null) return null;
    return lvcsFile.getRevision();
  }

  private LocalVcs getLocalVcs() {
    return LocalVcs.getInstance(myProject);
  }

  private void undoableActionPerformed(VirtualFileEvent event) {
    undoableActionPerformed(event.getFile());
  }

  private List<LvcsChange> getChanges(LvcsRevision requestedRevision, boolean isDir) {
    List<LvcsChange> changes = getChanges(requestedRevision,
                                          getCurrentRevision(getUpToDatePathFor(requestedRevision), isDir));
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
    return
      createChangesFromRevisions(collectRevisionsFromTo(requestedRevision, lastRevision));
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
      LOG.assertTrue(!currentRevision.isPurged());
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

    private final VirtualFile myFile;
    private final String myFilePath;
    private final boolean myDirectory;

    public LvcsBasedUndoableAction(VirtualFile file,
                                   String filePath,
                                   boolean directory,
                                   LvcsRevision afterActionPerformedRevision) {
      myFile = file;
      myFilePath = filePath;
      myDirectory = directory;
      myAfterActionPerformedRevision = afterActionPerformedRevision;
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
      return new DocumentReference[]{new DocumentReferenceByVirtualFile(myFile)};
    }

    public boolean isComplex() {
      return true;
    }

    public void actionCompleted() {
      myAfterActionPerformedRevision = myAfterActionPerformedRevision.findLatestRevision();
    }
  }

  public boolean itemCanBePurged(LvcsRevision revision) {
    synchronized (myLockedRevisions) {
      return !myLockedRevisions.contains(revision);
    }
  }
}
