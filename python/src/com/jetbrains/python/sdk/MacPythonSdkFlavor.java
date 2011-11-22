package com.jetbrains.python.sdk;

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author yole
 */
public class MacPythonSdkFlavor extends CPythonSdkFlavor {
  private MacPythonSdkFlavor() {
  }

  public static MacPythonSdkFlavor INSTANCE = new MacPythonSdkFlavor();

  @Override
  public Collection<String> suggestHomePaths() {
    List<String> candidates = new ArrayList<String>();
    collectPythonInstallations("/Library/Frameworks/Python.framework/Versions", candidates);
    collectPythonInstallations("/System/Library/Frameworks/Python.framework/Versions", candidates);
    return candidates;
  }

  private static void collectPythonInstallations(String pythonPath, List<String> candidates) {
    VirtualFile rootVDir = LocalFileSystem.getInstance().findFileByPath(pythonPath);
    if (rootVDir != null) {
      rootVDir.refresh(false, false);
      for (VirtualFile dir : rootVDir.getChildren()) {
        final String dir_name = dir.getName().toLowerCase();
        if (dir.isDirectory()) {
          if ("Current".equals(dir_name) || dir_name.startsWith("2") || dir_name.startsWith("3")) {
            VirtualFile bin_dir = dir.findChild("bin");
            if (bin_dir != null && bin_dir.isDirectory()) {
              VirtualFile python_exe = bin_dir.findChild("python");
              if (python_exe != null) candidates.add(python_exe.getPath());
            }
          }
        }
      }
    }
  }
}
