package org.jetbrains.plugins.ideaConfigurationServer.actions;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.actions.CommonCheckinFilesAction;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.plugins.ideaConfigurationServer.IcsBundle;
import org.jetbrains.plugins.ideaConfigurationServer.ProjectId;

class AddToIcsAction extends CommonCheckinFilesAction {
  @Override
  protected String getActionName(VcsContext dataContext) {
    FilePath[] roots = getRoots(dataContext);
    return IcsBundle.message("action.AddToIcs.text", roots == null ? 0 : roots.length);
  }

  @Override
  protected boolean isApplicableRoot(VirtualFile file, FileStatus status, VcsContext dataContext) {
    if (!super.isApplicableRoot(file, status, dataContext) || file.isDirectory()) {
      return false;
    }
    String projectConfigDirPath =
      ((ProjectEx)dataContext.getProject()).getStateStore().getStateStorageManager().expandMacros(StoragePathMacros.PROJECT_CONFIG_DIR);
    VirtualFile projectConfigDir = projectConfigDirPath == null ? null : LocalFileSystem.getInstance().findFileByPath(projectConfigDirPath);
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

    //IcsManager.getInstance()
    //for (VirtualFile file : context.getSelectedFiles()) {
    //  file.
    //}
  }
}