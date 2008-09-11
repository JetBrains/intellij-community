package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesBrowserUseCase;
import com.intellij.openapi.vcs.vfs.ContentRevisionVirtualFile;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.application.ModalityState;
import com.intellij.pom.Navigatable;

/**
 * @author yole
 */
public class OpenRepositoryVersionAction extends AnAction {
  public OpenRepositoryVersionAction() {
    // TODO[yole]: real icon
    super(VcsBundle.message("open.repository.version.text"), VcsBundle.message("open.repository.version.description"),
          IconLoader.getIcon("/objectBrowser/showEditorHighlighting.png"));
  }

  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    Change[] changes = e.getData(VcsDataKeys.SELECTED_CHANGES);
    assert changes != null;
    for(Change change: changes) {
      ContentRevision revision = change.getAfterRevision();
      if (revision == null || revision.getFile().isDirectory()) continue;
      VirtualFile vFile = ContentRevisionVirtualFile.create(revision);
      Navigatable navigatable = new OpenFileDescriptor(project, vFile);
      navigatable.navigate(true);
    }
  }

  public void update(final AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    Change[] changes = e.getData(VcsDataKeys.SELECTED_CHANGES);
    e.getPresentation().setEnabled(project != null && changes != null &&
                                   (! CommittedChangesBrowserUseCase.IN_AIR.equals(e.getDataContext().getData(CommittedChangesBrowserUseCase.CONTEXT_NAME))) &&
                                   hasValidChanges(changes) &&
                                   ModalityState.NON_MODAL.equals(ModalityState.current()));
  }

  private static boolean hasValidChanges(final Change[] changes) {
    for(Change c: changes) {
      final ContentRevision contentRevision = c.getAfterRevision();
      if (contentRevision != null && !contentRevision.getFile().isDirectory()) {
        return true;
      }
    }
    return false;
  }
}