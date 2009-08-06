package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.changes.DirtBuilder;
import com.intellij.openapi.vcs.changes.VcsGuess;
import com.intellij.openapi.vcs.changes.FilePathUnderVcs;
import com.intellij.openapi.vcs.impl.projectlevelman.NewMappings;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.ProjectBaseDirectory;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author yole
 */
public class BasicDefaultVcsRootPolicy extends DefaultVcsRootPolicy {
  private final Project myProject;
  private final VirtualFile myBaseDir;

  public BasicDefaultVcsRootPolicy(Project project) {
    myProject = project;
    myBaseDir = project.getBaseDir();
  }

  public void addDefaultVcsRoots(final NewMappings mappingList, final AbstractVcs vcs, final List<VirtualFile> result) {
    final VirtualFile baseDir = ProjectBaseDirectory.getInstance(myProject).getBaseDir(myBaseDir);
    if (baseDir != null && vcs.getName().equals(mappingList.getVcsFor(baseDir))) {
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

  public void markDefaultRootsDirty(final DirtBuilder builder, final VcsGuess vcsGuess) {
    final FilePathImpl fp = new FilePathImpl(ProjectBaseDirectory.getInstance(myProject).getBaseDir(myBaseDir));
    final AbstractVcs vcs = vcsGuess.getVcsForDirty(fp);
    if (vcs != null) {
      builder.addDirtyDirRecursively(new FilePathUnderVcs(fp, vcs));
    }
  }

}
