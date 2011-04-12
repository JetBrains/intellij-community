package com.jetbrains.python.sdk;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author yole
 */
public class WinPythonSdkFlavor extends PythonSdkFlavor {
  @NonNls static final private String PYTHON_STR = "python";

  public static WinPythonSdkFlavor INSTANCE = new WinPythonSdkFlavor();

  private WinPythonSdkFlavor() {
  }

  @Override
  public Collection<String> suggestHomePaths() {
    Set<String> candidates = new TreeSet<String>();
    findSubdirInstallations(candidates, "C:\\", PYTHON_STR, "python.exe");
    findSubdirInstallations(candidates, "C:\\Program Files\\", PYTHON_STR, "python.exe");
    findSubdirInstallations(candidates, "C:\\", "jython", "jython.bat");
    findInPath(candidates, "python.exe");
    findInPath(candidates, "jython.bat");
    return candidates;
  }

  public static void findInPath(Collection<String> candidates, String exeName) {
    final String path = System.getenv("PATH");
    for (String pathEntry : StringUtil.split(path, ";")) {
      if (pathEntry.startsWith("\"") && pathEntry.endsWith("\"")) {
        if (pathEntry.length() < 2) continue;
        pathEntry = pathEntry.substring(1, pathEntry.length()-1);
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
