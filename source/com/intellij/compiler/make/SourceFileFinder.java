package com.intellij.compiler.make;

import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.HashMap;

import java.util.Iterator;
import java.util.Map;

/**
 * Assumes that source roots in the project has not changed and caches the snapshot of source roots for effective searching
 * User: JEKA
 * Date: Jul 17, 2003
 * Time: 9:52:26 PM
 */
public class SourceFileFinder {
  private final Project myProject;
  private final CompileContext myCompileContext;
  private Map<VirtualFile, String> myProjectSourceRoots = null;

  public SourceFileFinder(Project project, CompileContext compileContext) {
    myProject = project;
    myCompileContext = compileContext;
  }

  public VirtualFile findSourceFile(String qualifiedName, String srcName) {
    String relativePath = MakeUtil.createRelativePathToSource(qualifiedName, srcName);
    Map<VirtualFile, String> dirs = getAllSourceRoots();
    if (!StringUtil.startsWithChar(relativePath, '/')) {
      relativePath = "/" + relativePath;
    }
    LocalFileSystem fs = LocalFileSystem.getInstance();
    for (Iterator it = dirs.keySet().iterator(); it.hasNext();) {
      final VirtualFile dir = (VirtualFile)it.next();
      final String prefix = dirs.get(dir);
      String path;
      if (prefix.length() > 0 ) {
        if (CompilerUtil.startsWith(relativePath, prefix)) {
          // if there is package prefix assigned to the root, the relative path should be corrected
          path = dir.getPath() + relativePath.substring(prefix.length() - 1);
        }
        else {
          // if there is package prefix, but the relative path does not match it, skip the root
          continue;
        }
      }
      else {
        path = dir.getPath() + relativePath;
      }
      VirtualFile file = fs.findFileByPath(path);
      if (file != null) {
        return file;
      }
    }
    return null;
  }

  private Map<VirtualFile, String> getAllSourceRoots() {
    if (myProjectSourceRoots == null) {
      myProjectSourceRoots = new HashMap<VirtualFile, String>();
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
          final Module[] allModules = ModuleManager.getInstance(myProject).getModules();
          for (int idx = 0; idx < allModules.length; idx++) {
            final VirtualFile[] sourceRoots = myCompileContext.getSourceRoots(allModules[idx]);
            for (int i = 0; i < sourceRoots.length; i++) {
              final VirtualFile sourceRoot = sourceRoots[i];
              String packageName = fileIndex.getPackageNameByDirectory(sourceRoot);
              myProjectSourceRoots.put(sourceRoot, packageName == null || packageName.length() == 0? "" : "/" + packageName.replace('.', '/') + "/");
            }
          }
        }
      });
    }
    return myProjectSourceRoots;
  }

}
