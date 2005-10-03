package com.intellij.openapi.vcs.update;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.ArrayList;

public interface ScopeInfo {
  FilePath[] getRoots(VcsContext context, final ActionInfo actionInfo);

  String getScopeName(VcsContext dataContext, final ActionInfo actionInfo);

  ScopeInfo PROJECT = new ScopeInfo() {
    public String getScopeName(VcsContext dataContext, final ActionInfo actionInfo) {
      return VcsBundle.message("update.project.scope.name");
    }

    public FilePath[] getRoots(VcsContext context, final ActionInfo actionInfo) {
      ArrayList<FilePath> result = new ArrayList<FilePath>();
      Project project = context.getProject();
      VirtualFile[] contentRoots = ProjectRootManager.getInstance(project).getContentRoots();
      for (VirtualFile contentRoot : contentRoots) {
        AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(contentRoot);
        if (vcs != null) {
          UpdateEnvironment updateEnvironment = actionInfo.getEnvironment(vcs);
          if (updateEnvironment != null) {
            result.add(new FilePathImpl(contentRoot));
          }
        }
      }
      return result.toArray(new FilePath[result.size()]);
    }
  };

  ScopeInfo FILES = new ScopeInfo() {
    public String getScopeName(VcsContext dataContext, final ActionInfo actionInfo) {
      FilePath[] roots = getRoots(dataContext, actionInfo);
      if (roots == null || roots.length == 0) {
        return VcsBundle.message("update.files.scope.name");
      }
      boolean directory = roots[0].isDirectory();
      if (roots.length == 1) {
        if (directory) {
          return VcsBundle.message("update.directory.scope.name");
        }
        else {
          return VcsBundle.message("update.file.scope.name");
        }
      }
      else {
        if (directory) {
          return VcsBundle.message("update.directories.scope.name");
        }
        else {
          return VcsBundle.message("update.files.scope.name");
        }
      }

    }

    public FilePath[] getRoots(VcsContext context, final ActionInfo actionInfo) {
      return context.getSelectedFilePaths();
    }

  };
}
