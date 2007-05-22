package com.intellij.openapi.vcs.update;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.ArrayList;

public interface ScopeInfo {
  FilePath[] getRoots(VcsContext context, final ActionInfo actionInfo);
  String getScopeName(VcsContext dataContext, final ActionInfo actionInfo);
  boolean filterExistsInVcs();

  ScopeInfo PROJECT = new ScopeInfo() {
    public String getScopeName(VcsContext dataContext, final ActionInfo actionInfo) {
      return VcsBundle.message("update.project.scope.name");
    }

    public boolean filterExistsInVcs() {
      return true;
    }

    public FilePath[] getRoots(VcsContext context, final ActionInfo actionInfo) {
      ArrayList<FilePath> result = new ArrayList<FilePath>();
      Project project = context.getProject();
      final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
      final AbstractVcs[] vcses = vcsManager.getAllActiveVcss();
      for(AbstractVcs vcs: vcses) {
        if (actionInfo.getEnvironment(vcs) != null) {
          final VirtualFile[] files = vcsManager.getRootsUnderVcs(vcs);
          for(VirtualFile file: files) {
            result.add(new FilePathImpl(file));
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

    public boolean filterExistsInVcs() {
      return true;
    }

    public FilePath[] getRoots(VcsContext context, final ActionInfo actionInfo) {
      return context.getSelectedFilePaths();
    }

  };
}
