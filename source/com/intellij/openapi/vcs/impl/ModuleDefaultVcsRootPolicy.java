package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeImpl;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManagerImpl;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author yole
 */
public class ModuleDefaultVcsRootPolicy extends DefaultVcsRootPolicy {
  private Project myProject;
  private final VirtualFile myBaseDir;

  public ModuleDefaultVcsRootPolicy(final Project project) {
    myProject = project;
    myBaseDir = project.getBaseDir();
  }

  public void addDefaultVcsRoots(final VcsDirectoryMappingList mappingList, final AbstractVcs vcs, final List<VirtualFile> result) {
    if (myBaseDir != null && vcs.getName().equals(mappingList.getVcsFor(myBaseDir)) && vcs.fileIsUnderVcs(new FilePathImpl(myBaseDir))) {
      result.add(myBaseDir);
    }
    final Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for(Module module: modules) {
      final VirtualFile[] files = ModuleRootManager.getInstance(module).getContentRoots();
      for(VirtualFile file: files) {
        // if we're currently processing moduleAdded notification, getModuleForFile() will return null, so we pass the module
        // explicitly (we know it anyway)
        VcsDirectoryMapping mapping = mappingList.getMappingFor(file, module);
        final String mappingVcs = mapping != null ? mapping.getVcs() : null;
        if (vcs.getName().equals(mappingVcs)) {
          result.add(file);
        }
      }
    }
  }

  public boolean matchesDefaultMapping(final VirtualFile file, final Object matchContext) {
    if (matchContext != null) {
      return true;
    }
    if (myBaseDir != null && VfsUtil.isAncestor(myBaseDir, file, false)) {
      return !ProjectRootManager.getInstance(myProject).getFileIndex().isIgnored(file);
    }
    return false;
  }

  @Nullable
  public Object getMatchContext(final VirtualFile file) {
    return ModuleUtil.findModuleForFile(file, myProject);
  }

  @Nullable
  public VirtualFile getVcsRootFor(final VirtualFile file) {
    if (myBaseDir != null && ExcludedFileIndex.getInstance(myProject).isValidAncestor(myBaseDir, file)) {
      return myBaseDir;
    }
    final VirtualFile contentRoot = ProjectRootManager.getInstance(myProject).getFileIndex().getContentRootForFile(file);
    if (contentRoot != null) {
      return contentRoot;
    }
    return null;
  }

  public void markDefaultRootsDirty(final VcsDirtyScopeManagerImpl vcsDirtyScopeManager) {
    for(Module module: ModuleManager.getInstance(myProject).getModules()) {
      final VirtualFile[] files = ModuleRootManager.getInstance(module).getContentRoots();
      for(VirtualFile file: files) {
        final AbstractVcs vcs = ProjectLevelVcsManager.getInstance(myProject).getVcsFor(file);
        if (vcs != null) {
          vcsDirtyScopeManager.getScope(vcs).addDirtyDirRecursively(new FilePathImpl(file));
        }
      }
    }

    final AbstractVcs[] abstractVcses = ProjectLevelVcsManager.getInstance(myProject).getAllActiveVcss();
    for(AbstractVcs vcs: abstractVcses) {
      final VcsDirtyScopeImpl scope = vcsDirtyScopeManager.getScope(vcs);
      final VirtualFile[] roots = ProjectLevelVcsManager.getInstance(myProject).getRootsUnderVcs(vcs);
      for(VirtualFile root: roots) {
        if (root.equals(myProject.getBaseDir())) {
          scope.addDirtyFile(new FilePathImpl(root));
        }
        else {
          scope.addDirtyDirRecursively(new FilePathImpl(root));
        }
      }
    }
  }
}