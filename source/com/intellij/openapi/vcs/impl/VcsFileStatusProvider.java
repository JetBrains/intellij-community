package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.readOnlyHandler.ReadonlyStatusHandlerImpl;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author yole
 */
public class VcsFileStatusProvider implements FileStatusProvider {
  private Project myProject;
  private final FileStatusManagerImpl myFileStatusManager;
  private final ProjectLevelVcsManager myVcsManager;
  private ChangeListManager myChangeListManager;
  private VcsDirtyScopeManager myDirtyScopeManager;

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.impl.VcsFileStatusProvider");

  public VcsFileStatusProvider(final Project project,
                               final FileStatusManagerImpl fileStatusManager,
                               final ProjectLevelVcsManager vcsManager,
                               ChangeListManager changeListManager,
                               VcsDirtyScopeManager dirtyScopeManager) {
    myProject = project;
    myFileStatusManager = fileStatusManager;
    myVcsManager = vcsManager;
    myChangeListManager = changeListManager;
    myDirtyScopeManager = dirtyScopeManager;
    myFileStatusManager.setFileStatusProvider(this);

    changeListManager.addChangeListListener(new ChangeListAdapter() {
      public void changeListAdded(ChangeList list) {
        fileStatusesChanged();
      }

      public void changeListRemoved(ChangeList list) {
        fileStatusesChanged();
      }

      public void changeListChanged(ChangeList list) {
        fileStatusesChanged();
      }

      public void changeListUpdateDone() {
        ProjectLevelVcsManagerImpl vcsManager = (ProjectLevelVcsManagerImpl) myVcsManager;
        if (vcsManager.hasEmptyContentRevisions()) {
          vcsManager.resetHaveEmptyContentRevisions();
          fileStatusesChanged();
        }
      }

      @Override public void unchangedFileStatusChanged() {
        fileStatusesChanged();
      }
    });
  }

  private void fileStatusesChanged() {
    myFileStatusManager.fileStatusesChanged();
  }

  public FileStatus getFileStatus(final VirtualFile virtualFile) {
    final AbstractVcs vcs = myVcsManager.getVcsFor(virtualFile);
    if (vcs == null) return FileStatus.NOT_CHANGED;

    final FileStatus status = myChangeListManager.getStatus(virtualFile);
    if (status == FileStatus.NOT_CHANGED && isDocumentModified(virtualFile)) return FileStatus.MODIFIED;
    return status;
  }

  private static boolean isDocumentModified(VirtualFile virtualFile) {
    if (virtualFile.isDirectory()) return false;
    final Document editorDocument = FileDocumentManager.getInstance().getCachedDocument(virtualFile);

    if (editorDocument != null && editorDocument.getModificationStamp() != virtualFile.getModificationStamp()) {
      return true;
    }

    return false;
  }

  public void refreshFileStatusFromDocument(final VirtualFile file, final Document doc) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("refreshFileStatusFromDocument: file.getModificationStamp()=" + file.getModificationStamp() + ", document.getModificationStamp()=" + doc.getModificationStamp());
    }
    FileStatus cachedStatus = myFileStatusManager.getCachedStatus(file);
    if (cachedStatus == FileStatus.NOT_CHANGED || file.getModificationStamp() == doc.getModificationStamp()) {
      final AbstractVcs vcs = myVcsManager.getVcsFor(file);
      if (vcs == null) return;
      if (cachedStatus == FileStatus.MODIFIED && file.getModificationStamp() == doc.getModificationStamp()) {
        if (!((ReadonlyStatusHandlerImpl) ReadonlyStatusHandlerImpl.getInstance(myProject)).getState().SHOW_DIALOG) {
          RollbackEnvironment rollbackEnvironment = vcs.getRollbackEnvironment();
          if (rollbackEnvironment != null) {
            rollbackEnvironment.rollbackIfUnchanged(file);
          }
        }
      }
      myFileStatusManager.fileStatusChanged(file);
      ChangeProvider cp = vcs.getChangeProvider();
      if (cp != null && cp.isModifiedDocumentTrackingRequired()) {
        myDirtyScopeManager.fileDirty(file);
      }
    }
  }
}