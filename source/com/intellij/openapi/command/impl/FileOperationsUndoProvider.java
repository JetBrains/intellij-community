package com.intellij.openapi.command.impl;

import com.intellij.localVcs.changes.LocalVcsChanges;
import com.intellij.localVcs.changes.LvcsChange;
import com.intellij.openapi.Disposeable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.localVcs.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.*;

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
  private Collection<RepositoryItem> myLockedRevisions = new HashSet<RepositoryItem>();
  private final Key<CompositeUndoableAction> DELETE_UNDOABLE_ACTION_KEY = new Key<CompositeUndoableAction>("DeleteUndoableAction");

  private static class CompositeUndoableAction implements UndoableAction, Disposeable {
    private final List<MyUndoableAction> myActions = new ArrayList<MyUndoableAction>();

    public CompositeUndoableAction addAction(MyUndoableAction action) {
      myActions.add(action);
      return this;
    }

    public void undo() throws UnexpectedUndoException {
      for (Iterator each = myActions.iterator(); each.hasNext();) {
        UndoableAction undoableAction = (UndoableAction)each.next();
        undoableAction.undo();
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
      for (Iterator each = myActions.iterator(); each.hasNext();) {
        UndoableAction undoableAction = (UndoableAction)each.next();
        result.addAll(Arrays.asList(undoableAction.getAffectedDocuments()));
      }
      return result.toArray(new DocumentReference[result.size()]);
    }

    public boolean isComplex() {
      return true;
    }

    public void dispose() {
      for (Iterator each = myActions.iterator(); each.hasNext();) {
        Disposeable undoableAction = (Disposeable)each.next();
        undoableAction.dispose();
      }

    }

    public boolean isEmpty() {
      return myActions.isEmpty();
    }

    public void actionCompleted() {
      for (Iterator each = myActions.iterator(); each.hasNext();) {
        MyUndoableAction action = (MyUndoableAction)each.next();
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
    undoableActionPerformed(event);
  }

  public void contentsChanged(VirtualFileEvent event) {
  }

  public void fileCreated(VirtualFileEvent event) {
    if (event.getRequestor() == null) {
      createNonUndoableAction(event.getFile(), true);
    }
    else {
      undoableActionPerformed(event);
    }
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
    undoableActionPerformed(event);
  }

  public void beforePropertyChange(VirtualFilePropertyEvent event) {
  }

  public void beforeContentsChange(VirtualFileEvent event) {
    if (event.getRequestor() == null) {
      createNonUndoableAction(event.getFile(), false);
    }
  }

  public void beforeFileDeletion(VirtualFileEvent event) {
    VirtualFile file = event.getFile();
    if (event.getRequestor() == null) {
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
    if (getLocalVcs() == null || !getLocalVcs().isUnderVcs(vFile)) {
      return;
    }

    DocumentReference oldRef = myUndoManager.findInvalidatedReferenceByUrl(vFile.getUrl());
    final DocumentReference newRef = new DocumentReferenceByVirtualFile(vFile);

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
    if (!getLocalVcs().isUnderVcs(vFile)) return;
    final String filePath = vFile.getPath();
    final boolean isDirectory = vFile.isDirectory();
    final LvcsRevision afterActionPerformedRevision = getCurrentRevision(filePath, isDirectory);
    if (afterActionPerformedRevision == null) return;
    synchronized (myLockedRevisions) {
      myLockedRevisions.add(afterActionPerformedRevision.getItem());
    }
    MyUndoableAction action = new MyUndoableAction(vFile, filePath, isDirectory, afterActionPerformedRevision);
    compositeUndoableAction.addAction(action);

    VirtualFile[] children = vFile.getChildren();
    if (children == null) return;
    for (int i = 0; i < children.length; i++) {
      addActionForFileTo(compositeUndoableAction, children[i]);
    }

  }

  private LvcsRevision getCurrentRevision(String filePath, boolean isDir) {
    LocalVcs vcs = getLocalVcs();
    LvcsObject lvcsFile = isDir ? (LvcsObject)vcs.findDirectory(filePath, true) : vcs.findFile(filePath, true);
    if (lvcsFile == null) return null;
    LvcsRevision revision = lvcsFile.getRevision();
    return revision;
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
    for (Iterator iterator = changes.iterator(); iterator.hasNext();) {
      LvcsChange lvcsChange = (LvcsChange)iterator.next();
      if (lvcsChange.getChangeType() != LvcsChange.CONTENT_CHANGED) result.add(lvcsChange);
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
      LOG.assertTrue(!currentRevision.getItem().isPurged());
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

  private class MyUndoableAction implements UndoableAction, Disposeable {
    private LvcsRevision myBeforeUndoRevision;
    private LvcsRevision myAfterActionPerformedRevision;

    private VirtualFile myFile;
    private final String myFilePath;
    private final boolean myDirectory;

    public MyUndoableAction(VirtualFile file,
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
        if (myBeforeUndoRevision != null) myLockedRevisions.remove(myBeforeUndoRevision.getItem());
        if (myAfterActionPerformedRevision != null) myLockedRevisions.remove(myAfterActionPerformedRevision.getItem());
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
            myLockedRevisions.add(myBeforeUndoRevision.getItem());
          }
        }
      }
      catch (final IOException e) {
        myBeforeUndoRevision = null;
        Application application = ApplicationManager.getApplication();
        if (!application.isUnitTestMode()) {
          Runnable showDialogAction = new Runnable() {
            public void run() {
              Messages.showErrorDialog("Cannot undo: " + e.getLocalizedMessage(), "Cannot Undo");
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
        return;
      }

    }

    public void redo() throws UnexpectedUndoException {
      try {
        rollbackTo(myBeforeUndoRevision);
      }
      catch (IOException e) {
        if (!ApplicationManager.getApplication().isUnitTestMode()) {
          Messages.showErrorDialog("Cannot redo: " + e.getLocalizedMessage(), "Cannot Redo");
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
      return !myLockedRevisions.contains(revision.getItem());
    }
  }
}
