package com.jetbrains.python.sdk;

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class MacPythonSdkFlavor extends PythonSdkFlavor {
  private MacPythonSdkFlavor() {
  }

  public static MacPythonSdkFlavor INSTANCE = new MacPythonSdkFlavor();

  @Override
  public List<String> suggestHomePaths() {
    List<String> candidates = new ArrayList<String>();
    final String pythonPath = "/Library/Frameworks/Python.framework/Versions";
    VirtualFile rootVDir = LocalFileSystem.getInstance().findFileByPath(pythonPath);
    if (rootVDir != null) {
      for (VirtualFile dir : rootVDir.getChildren()) {
        final String dir_name = dir.getName().toLowerCase();
        if (dir.isDirectory()) {
          // TODO why would Jython be ever installed under /Library/Frameworks?
          if ("Current".equals(dir_name) || dir_name.startsWith("2") || dir_name.startsWith("3") || dir_name.startsWith("jython")) {
            VirtualFile bin_dir = dir.findChild("bin");
            if (bin_dir != null && bin_dir.isDirectory()) {
              VirtualFile python_exe = dir.findChild("python");
              if (python_exe != null) candidates.add(python_exe.getPath());
              python_exe = dir.findChild("jython"); // maybe it's in bin/
              if (python_exe != null) candidates.add(python_exe.getPath());
            }
            else {
              VirtualFile python_exe = dir.findChild("jython"); // maybe it's not in bin/
              if (python_exe != null) candidates.add(python_exe.getPath());
            }
          }
        }
      }
    }
    return candidates;
  }
}
