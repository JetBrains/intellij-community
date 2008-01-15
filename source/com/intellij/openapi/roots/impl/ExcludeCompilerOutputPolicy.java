package com.intellij.openapi.roots.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;

/**
 * @author yole
 */
public class ExcludeCompilerOutputPolicy implements DirectoryIndexExcludePolicy {
  private Project myProject;

  public ExcludeCompilerOutputPolicy(final Project project) {
    myProject = project;
  }

  public boolean isExcludeRoot(final VirtualFile f) {
    CompilerProjectExtension compilerProjectExtension = CompilerProjectExtension.getInstance(myProject);
    if (DirectoryIndexImpl.isEqualWithFileOrUrl(f, compilerProjectExtension.getCompilerOutput(), compilerProjectExtension.getCompilerOutputUrl())) return true;

    for (Module m : ModuleManager.getInstance(myProject).getModules()) {
      CompilerModuleExtension rm = CompilerModuleExtension.getInstance(m);
      if (DirectoryIndexImpl.isEqualWithFileOrUrl(f, rm.getCompilerOutputPath(), rm.getCompilerOutputUrl())) return true;
      if (DirectoryIndexImpl.isEqualWithFileOrUrl(f, rm.getCompilerOutputPathForTests(), rm.getCompilerOutputUrlForTests())) return true;
    }
    return false;
  }

  public boolean isExcludeRootForModule(final Module module, final VirtualFile excludeRoot) {
    final CompilerModuleExtension compilerModuleExtension = CompilerModuleExtension.getInstance(module);
    return compilerModuleExtension.getCompilerOutputPath() == excludeRoot || compilerModuleExtension.getCompilerOutputPathForTests() == excludeRoot;
  }

  public VirtualFile[] getExcludeRootsForProject() {
    VirtualFile outputPath = CompilerProjectExtension.getInstance(myProject).getCompilerOutput();
    if (outputPath != null) {
      return new VirtualFile[] { outputPath };
    }
    return VirtualFile.EMPTY_ARRAY;
  }
}
