package com.jetbrains.python.sdk;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * User : catherine
 */
public class VirtualEnvSdkFlavor extends CPythonSdkFlavor {
  private VirtualEnvSdkFlavor() {
  }

  private final static String[] NAMES = new String[]{"python", "jython", "pypy", "python.exe", "jython.bat", "pypy.exe"};

  public static VirtualEnvSdkFlavor INSTANCE = new VirtualEnvSdkFlavor();

  @Override
  public Collection<String> suggestHomePaths() {
    final Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext());
    List<String> candidates = new ArrayList<String>();
    if (project != null) {
      VirtualFile rootDir = project.getBaseDir();
      if (rootDir != null)
        candidates.addAll(findInDirectory(rootDir));
    }
    return candidates;
  }

  private Collection<String> findInDirectory(VirtualFile rootDir) {
    List<String> candidates = new ArrayList<String>();
    if (rootDir != null) {
      VirtualFile[] suspects = rootDir.getChildren();
      for (VirtualFile child : suspects) {
        if (child.isDirectory()) {
          final String childName = child.getName();
          if (childName.equals("bin") || childName.equals("Scripts"))
            candidates.addAll(findInterpreter(child));
          else
            candidates.addAll(findInDirectory(child));
        }
      }
    }
    return candidates;
  }

  private Collection<String> findInterpreter(VirtualFile dir) {
    List<String> candidates = new ArrayList<String>();
    for (VirtualFile child : dir.getChildren()) {
      if (!child.isDirectory()) {
        final String childName = child.getName();
        for (String name : NAMES) {
          if (childName.startsWith(name)) {
            if (!childName.endsWith("-config")) candidates.add(child.getPath());
            break;
          }
        }
      }
    }
    return candidates;
  }
}
