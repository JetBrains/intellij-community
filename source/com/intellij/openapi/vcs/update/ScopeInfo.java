package com.intellij.openapi.vcs.update;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.ArrayList;

public interface ScopeInfo {
  FilePath[] getRoots(VcsContext context);

  String getScopeName(VcsContext dataContext);

  ScopeInfo PROJECT = new ScopeInfo() {
    public String getScopeName(VcsContext dataContext) {
      return "Project";
    }

    public FilePath[] getRoots(VcsContext context) {
      ArrayList<FilePath> result = new ArrayList<FilePath>();
      Project project = context.getProject();
      VirtualFile[] contentRoots = ProjectRootManager.getInstance(project).getContentRoots();
      for (int i = 0; i < contentRoots.length; i++) {
        VirtualFile contentRoot = contentRoots[i];
        AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(contentRoot);
        if (vcs != null) {
          UpdateEnvironment updateEnvironment = vcs.getUpdateEnvironment();
          if (updateEnvironment != null) {
            result.add(new FilePathImpl(contentRoot));
          }
        }
      }
      return result.toArray(new FilePath[result.size()]);
    }
  };

  ScopeInfo FILES = new ScopeInfo() {
    public String getScopeName(VcsContext dataContext) {
      FilePath[] roots = getRoots(dataContext);
      if (roots == null || roots.length == 0) {
        return "Files";
      }
      boolean directory = roots[0].isDirectory();
      if (roots.length == 1) {
        if (directory) {
          return "Directory";
        }
        else {
          return "File";
        }
      }
      else {
        if (directory) {
          return "Directories";
        }
        else {
          return "Files";
        }
      }

    }

    public FilePath[] getRoots(VcsContext context) {
      return context.getSelectedFilePaths();
    }

  };
}
