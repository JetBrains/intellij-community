package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManagerImpl;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.ProjectBaseDirectory;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author yole
 */
public class BasicDefaultVcsRootPolicy extends DefaultVcsRootPolicy {
  private Project myProject;
  private VirtualFile myBaseDir;

  public BasicDefaultVcsRootPolicy(Project project) {
    myProject = project;
    myBaseDir = project.getBaseDir();
  }

  public void addDefaultVcsRoots(final VcsDirectoryMappingList mappingList, final String vcsName, final List<VirtualFile> result) {
    final VirtualFile baseDir = ProjectBaseDirectory.getInstance(myProject).getBaseDir(myBaseDir);
    if (baseDir != null && vcsName.equals(mappingList.getVcsFor(baseDir))) {
      result.add(baseDir);
    }
  }

  public boolean matchesDefaultMapping(final VirtualFile file, final Object matchContext) {
    return VfsUtil.isAncestor(ProjectBaseDirectory.getInstance(myProject).getBaseDir(myBaseDir), file, false);
  }

  @Nullable
  public Object getMatchContext(final VirtualFile file) {
    return null;
  }

  @Nullable
  public VirtualFile getVcsRootFor(final VirtualFile file) {
    return ProjectBaseDirectory.getInstance(myProject).getBaseDir(myBaseDir);
  }

  public void markDefaultRootsDirty(final VcsDirtyScopeManagerImpl vcsDirtyScopeManager) {
    vcsDirtyScopeManager.dirDirtyRecursively(ProjectBaseDirectory.getInstance(myProject).getBaseDir(myBaseDir));
  }

}
