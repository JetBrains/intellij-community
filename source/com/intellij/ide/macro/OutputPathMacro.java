package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.compiler.ex.CompilerPathsEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.ide.IdeBundle;

import java.io.File;

public final class OutputPathMacro extends Macro {
  public String getName() {
    return "OutputPath";
  }

  public String getDescription() {
    return IdeBundle.message("macro.output.path");
  }

  public String expand(DataContext dataContext) {
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) {
      return null;
    }

    VirtualFile file = (VirtualFile)dataContext.getData(DataConstants.VIRTUAL_FILE);
    if (file != null) {
      ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
      Module module = projectFileIndex.getModuleForFile(file);
      if (module != null){
        boolean isTest = projectFileIndex.isInTestSourceContent(file);
        ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
        String outputPathUrl = isTest ? moduleRootManager.getCompilerOutputPathForTestsUrl() : moduleRootManager.getCompilerOutputPathUrl();
        if (outputPathUrl == null) return null;
        return VirtualFileManager.extractPath(outputPathUrl).replace('/', File.separatorChar);
      }
    }

    Module[] allModules = ModuleManager.getInstance(project).getSortedModules();
    if (allModules.length == 0) {
      return null;
    }
    String[] paths = CompilerPathsEx.getOutputPaths(allModules);
    final StringBuffer outputPath = new StringBuffer();
    for (int idx = 0; idx < paths.length; idx++) {
      String path = paths[idx];
      if (idx > 0) {
        outputPath.append(File.pathSeparator);
      }
      outputPath.append(path);
    }
    return outputPath.toString();
  }
}
