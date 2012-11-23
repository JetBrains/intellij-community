package com.jetbrains.python.sdk.flavors;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;

import java.io.File;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author yole
 */
public class WinPythonSdkFlavor extends CPythonSdkFlavor {
  public static WinPythonSdkFlavor INSTANCE = new WinPythonSdkFlavor();

  private WinPythonSdkFlavor() {
  }

  @Override
  public Collection<String> suggestHomePaths() {
    Set<String> candidates = new TreeSet<String>();
    findInCandidatePaths(candidates, "python.exe", "jython.bat", "pypy.exe");
    return candidates;
  }

  private static void findInCandidatePaths(Set<String> candidates, String... exe_names) {
    for (String name : exe_names) {
      findInstallations(candidates, name, "C:\\", "C:\\Program Files\\");
      findInPath(candidates, name);
    }
  }

  private static void findInstallations(Set<String> candidates, String exe_name, String... roots) {
    for (String root : roots) {
      findSubdirInstallations(candidates, root, FileUtil.getNameWithoutExtension(exe_name), exe_name);
    }
  }

  public static void findInPath(Collection<String> candidates, String exeName) {
    final String path = System.getenv("PATH");
    for (String pathEntry : StringUtil.split(path, ";")) {
      if (pathEntry.startsWith("\"") && pathEntry.endsWith("\"")) {
        if (pathEntry.length() < 2) continue;
        pathEntry = pathEntry.substring(1, pathEntry.length() - 1);
      }
      File f = new File(pathEntry, exeName);
      if (f.exists()) {
        candidates.add(FileUtil.toSystemIndependentName(f.getPath()));
      }
    }
  }

  private static void findSubdirInstallations(Collection<String> candidates, String rootDir, String dir_prefix, String exe_name) {
    VirtualFile rootVDir = LocalFileSystem.getInstance().findFileByPath(rootDir);
    if (rootVDir != null) {
      if (rootVDir instanceof NewVirtualFile) {
        ((NewVirtualFile)rootVDir).markDirty();
      }
      rootVDir.refresh(false, false);
      for (VirtualFile dir : rootVDir.getChildren()) {
        if (dir.isDirectory() && dir.getName().toLowerCase().startsWith(dir_prefix)) {
          VirtualFile python_exe = dir.findChild(exe_name);
          if (python_exe != null) candidates.add(FileUtil.toSystemIndependentName(python_exe.getPath()));
        }
      }
    }
  }
}
