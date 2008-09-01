package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.impl.ExcludedFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import org.tmatesoft.svn.core.SVNException;

public class SvnExcludingIgnoredOperation {
  private final Project myProject;
  private final Operation myImportAction;
  private final boolean myRecursive;
  private final ExcludedFileIndex myIndex;
  private final ChangeListManager myClManager;

  public SvnExcludingIgnoredOperation(final Project project, final Operation importAction, final boolean recursive) {
    myProject = project;
    myImportAction = importAction;
    myRecursive = recursive;

    if (! project.isDefault()) {
      myIndex = ExcludedFileIndex.getInstance(project);
      myClManager = ChangeListManager.getInstance(project);
    } else {
      myIndex = null;
      myClManager = null;
    }
  }

  public void execute(final VirtualFile file) throws SVNException {
    if (! myProject.isDefault()) {
      if (myIndex.isExcludedFile(file)) {
        return;
      }
      if (myClManager.isIgnoredFile(file)) {
        return;
      }
    }

    myImportAction.doOperation(file);

    if (myRecursive) {
      for (VirtualFile child : file.getChildren()) {
        execute(child);
      }
    }
  }

  public interface Operation {
    void doOperation(final VirtualFile file) throws SVNException;
  }
}
