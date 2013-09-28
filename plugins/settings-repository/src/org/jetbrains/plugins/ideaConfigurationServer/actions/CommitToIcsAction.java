package org.jetbrains.plugins.ideaConfigurationServer.actions;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.actions.CommonCheckinFilesAction;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ideaConfigurationServer.CommitToIcsDialog;
import org.jetbrains.plugins.ideaConfigurationServer.IcsBundle;
import org.jetbrains.plugins.ideaConfigurationServer.ProjectId;

import java.util.List;

class CommitToIcsAction extends CommonCheckinFilesAction {
  @Override
  protected String getActionName(VcsContext dataContext) {
    FilePath[] roots = getRoots(dataContext);
    return IcsBundle.message("action.AddToIcs.text", roots == null ? 0 : roots.length);
  }

  @Override
  protected boolean isApplicableRoot(VirtualFile file, FileStatus status, VcsContext dataContext) {
    return ((ProjectEx)dataContext.getProject()).getStateStore().getStorageScheme() == StorageScheme.DIRECTORY_BASED &&
           super.isApplicableRoot(file, status, dataContext) &&
           !file.isDirectory() &&
           isProjectConfigFile(file, dataContext.getProject());
  }

  private static boolean isProjectConfigFile(@Nullable VirtualFile file, Project project) {
    if (file == null) {
      return false;
    }

    VirtualFile projectFile = project.getProjectFile();
    VirtualFile projectConfigDir = projectFile == null ? null : projectFile.getParent();
    return projectConfigDir != null && VfsUtilCore.isAncestor(projectConfigDir, file, true);
  }

  @Override
  protected FilePath[] prepareRootsForCommit(FilePath[] roots, Project project) {
    return null;
  }

  @Override
  protected void performCheckIn(VcsContext context, Project project, FilePath[] roots) {
    ProjectId projectId = ServiceManager.getService(project, ProjectId.class);
    if (projectId.uid == null) {

    }

    List<Change> projectFileChanges = null;
    Change[] changes = context.getSelectedChanges();
    if (changes != null && changes.length > 0) {
      for (Change change : changes) {
        if (isProjectConfigFile(change.getVirtualFile(), project)) {
          if (projectFileChanges == null) {
            projectFileChanges = new SmartList<Change>();
          }
          projectFileChanges.add(change);
        }
      }
    }
    else {
      ChangeListManager manager = ChangeListManager.getInstance(project);
      FilePath[] paths = getRoots(context);
      for (FilePath path : paths) {
        if (isProjectConfigFile(path.getVirtualFile(), project)) {
          for (Change change : manager.getChangesIn(path)) {
            if (projectFileChanges == null) {
              projectFileChanges = new SmartList<Change>();
            }
            projectFileChanges.add(change);
          }
        }
      }
    }

    if (projectFileChanges == null) {
      return;
    }

    new CommitToIcsDialog(project, projectFileChanges).show();
  }
}