/*
 * @author: Eugene Zhuravlev
 * Date: Jan 20, 2003
 * Time: 5:34:19 PM
 */
package com.intellij.compiler.impl;

import com.intellij.openapi.compiler.CompilerPaths;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFileManager;

import java.io.File;

public class ProjectCompileScope extends FileIndexCompileScope {
  private final Project myProject;
  private final String myTempDirUrl;

  public ProjectCompileScope(final Project project) {
    myProject = project;
    final String path = CompilerPaths.getCompilerSystemDirectory(project).getPath().replace(File.separatorChar, '/');
    myTempDirUrl = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, path) + '/';
  }

  protected FileIndex[] getFileIndices() {
    return new FileIndex[] {ProjectRootManager.getInstance(myProject).getFileIndex()};
  }

  public boolean belongs(String url) {
    //return !url.startsWith(myTempDirUrl);
    return !FileUtil.startsWith(url, myTempDirUrl);
  }

  public Module[] getAffectedModules() {
    return ModuleManager.getInstance(myProject).getModules();
  }
}
