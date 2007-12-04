package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author yole
 */
public abstract class DefaultVcsRootPolicy {
  public static DefaultVcsRootPolicy getInstance(Project project) {
    return ServiceManager.getService(project, DefaultVcsRootPolicy.class);
  }

  public abstract void addDefaultVcsRoots(final VcsDirectoryMappingList mappingList, String vcsName, List<VirtualFile> result);

  public abstract boolean matchesDefaultMapping(final VirtualFile file, final Object matchContext);

  @Nullable
  public abstract Object getMatchContext(final VirtualFile file);

  @Nullable
  public abstract VirtualFile getVcsRootFor(final VirtualFile file);
}