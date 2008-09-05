package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.impl.ExcludedFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;

public class SvnExcludingIgnoredOperation {
  private final Project myProject;
  private final Operation myImportAction;
  private final SVNDepth myDepth;
  private final ExcludedFileIndex myIndex;
  private final ChangeListManager myClManager;

  public SvnExcludingIgnoredOperation(final Project project, final Operation importAction, final SVNDepth depth) {
    myProject = project;
    myImportAction = importAction;
    myDepth = depth;

    if (! project.isDefault()) {
      myIndex = ExcludedFileIndex.getInstance(project);
      myClManager = ChangeListManager.getInstance(project);
    } else {
      myIndex = null;
      myClManager = null;
    }
  }

  private boolean operation(final VirtualFile file) throws SVNException {
    if (! myProject.isDefault()) {
      if (myIndex.isExcludedFile(file)) {
        return false;
      }
      if (myClManager.isIgnoredFile(file)) {
        return false;
      }
    }

    myImportAction.doOperation(file);
    return true;
  }

  private void executeDown(final VirtualFile file) throws SVNException {
    if (! operation(file)) {
      return;
    }

    for (VirtualFile child : file.getChildren()) {
      executeDown(child);
    }
  }

  public void execute(final VirtualFile file) throws SVNException {
    if (SVNDepth.INFINITY.equals(myDepth)) {
      executeDown(file);
      return;
    }

    if (! operation(file)) {
      return;
    }

    if (SVNDepth.EMPTY.equals(myDepth)) {
      return;
    }

    for (VirtualFile child : file.getChildren()) {
      if (SVNDepth.FILES.equals(myDepth) && child.isDirectory()) {
        continue;
      }
      operation(child);
    }
  }

  public interface Operation {
    void doOperation(final VirtualFile file) throws SVNException;
  }
}
