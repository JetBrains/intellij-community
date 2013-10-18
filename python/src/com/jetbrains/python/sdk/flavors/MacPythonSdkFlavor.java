package com.jetbrains.python.sdk.flavors;

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;

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
  private static final String[] POSSIBLE_BINARY_NAMES = {"python", "python2", "python3"};

  @Override
  public Collection<String> suggestHomePaths() {
    List<String> candidates = new ArrayList<String>();
    collectPythonInstallations("/Library/Frameworks/Python.framework/Versions", candidates);
    collectPythonInstallations("/System/Library/Frameworks/Python.framework/Versions", candidates);
    UnixPythonSdkFlavor.collectUnixPythons("/usr/local/bin", candidates);
    return candidates;
  }

  private static void collectPythonInstallations(String pythonPath, List<String> candidates) {
    VirtualFile rootVDir = LocalFileSystem.getInstance().findFileByPath(pythonPath);
    if (rootVDir != null) {
      if (rootVDir instanceof NewVirtualFile) {
        ((NewVirtualFile)rootVDir).markDirty();
      }
      rootVDir.refresh(false, false);
      for (VirtualFile dir : rootVDir.getChildren()) {
        final String dir_name = dir.getName().toLowerCase();
        if (dir.isDirectory()) {
          if ("Current".equals(dir_name) || dir_name.startsWith("2") || dir_name.startsWith("3")) {
            final VirtualFile binDir = dir.findChild("bin");
            if (binDir != null && binDir.isDirectory()) {
              for (String name : POSSIBLE_BINARY_NAMES) {
                final VirtualFile child = binDir.findChild(name);
                if (child != null) {
                  candidates.add(child.getPath());
                  break;
                }
              }
            }
          }
        }
      }
    }
  }
}
