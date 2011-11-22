package com.jetbrains.python.sdk;

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author yole
 */
public class UnixPythonSdkFlavor extends CPythonSdkFlavor {
  private UnixPythonSdkFlavor() {
  }

  private final static String[] NAMES = new String[]{"python", "jython", "pypy"};

  public static UnixPythonSdkFlavor INSTANCE = new UnixPythonSdkFlavor();

  @Override
  public Collection<String> suggestHomePaths() {
    List<String> candidates = new ArrayList<String>();
    VirtualFile rootDir = LocalFileSystem.getInstance().findFileByPath("/usr/bin");
    if (rootDir != null) {
      VirtualFile[] suspects = rootDir.getChildren();
      for (VirtualFile child : suspects) {
        if (!child.isDirectory()) {
          final String child_name = child.getName();
          for (String name : NAMES) {
            if (child_name.startsWith(name)) {
              if (!child_name.endsWith("-config")) candidates.add(child.getPath());
              break;
            }
          }
        }
      }
    }
    return candidates;
  }
}
