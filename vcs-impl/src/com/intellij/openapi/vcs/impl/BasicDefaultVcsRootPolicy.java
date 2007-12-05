package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManagerImpl;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.PlatformProjectOpenProcessor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author yole
 */
public class BasicDefaultVcsRootPolicy extends DefaultVcsRootPolicy {
  private VirtualFile myBaseDir;

  public BasicDefaultVcsRootPolicy(Project project) {
    myBaseDir = project.getBaseDir();
  }

  public void addDefaultVcsRoots(final VcsDirectoryMappingList mappingList, final String vcsName, final List<VirtualFile> result) {
    final VirtualFile baseDir = PlatformProjectOpenProcessor.getBaseDir(myBaseDir);
    if (baseDir != null && vcsName.equals(mappingList.getVcsFor(baseDir))) {
      result.add(baseDir);
    }
  }

  public boolean matchesDefaultMapping(final VirtualFile file, final Object matchContext) {
    return VfsUtil.isAncestor(PlatformProjectOpenProcessor.getBaseDir(myBaseDir), file, false);
  }

  @Nullable
  public Object getMatchContext(final VirtualFile file) {
    return null;
  }

  @Nullable
  public VirtualFile getVcsRootFor(final VirtualFile file) {
    return PlatformProjectOpenProcessor.getBaseDir(myBaseDir);
  }

  public void markDefaultRootsDirty(final VcsDirtyScopeManagerImpl vcsDirtyScopeManager) {
    vcsDirtyScopeManager.dirDirtyRecursively(PlatformProjectOpenProcessor.getBaseDir(myBaseDir));
  }

}
