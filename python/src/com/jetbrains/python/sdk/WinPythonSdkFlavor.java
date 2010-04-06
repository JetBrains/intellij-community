package com.jetbrains.python.sdk;

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author yole
 */
public class WinPythonSdkFlavor extends PythonSdkFlavor {
  @NonNls static final private String PYTHON_STR = "python";

  public static WinPythonSdkFlavor INSTANCE = new WinPythonSdkFlavor();

  private WinPythonSdkFlavor() {
  }

  @Override
  public List<String> suggestHomePaths() {
    List<String> candidates = new ArrayList<String>();
    findSubdirInstallations(candidates, "C:\\", PYTHON_STR, "python.exe");
    findSubdirInstallations(candidates, "C:\\Program Files\\", PYTHON_STR, "python.exe");
    findSubdirInstallations(candidates, "C:\\", "jython", "jython.bat");
    return candidates;
  }

  private static void findSubdirInstallations(Collection<String> candidates, String rootDir, String dir_prefix, String exe_name) {
    VirtualFile rootVDir = LocalFileSystem.getInstance().findFileByPath(rootDir);
    if (rootVDir != null) {
      for (VirtualFile dir : rootVDir.getChildren()) {
        if (dir.isDirectory() && dir.getName().toLowerCase().startsWith(dir_prefix)) {
          VirtualFile python_exe = dir.findChild(exe_name);
          if (python_exe != null) candidates.add(python_exe.getPath());
        }
      }
    }
  }
}
