package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
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

  public void addDefaultVcsRoots(final VcsDirectoryMappingList mappingList, final String vcsName, final List<VirtualFile> result) {
    if (myBaseDir != null && vcsName.equals(mappingList.getVcsFor(myBaseDir))) {
      result.add(myBaseDir);
    }
    final Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for(Module module: modules) {
      final VirtualFile[] files = ModuleRootManager.getInstance(module).getContentRoots();
      for(VirtualFile file: files) {
        // if we're currently processing moduleAdded notification, getModuleForFile() will return null, so we pass the module
        // explicitly (we know it anyway)
        VcsDirectoryMapping mapping = mappingList.getMappingFor(file, module);
        final String vcs = mapping != null ? mapping.getVcs() : null;
        if (vcsName.equals(vcs)) {
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
}