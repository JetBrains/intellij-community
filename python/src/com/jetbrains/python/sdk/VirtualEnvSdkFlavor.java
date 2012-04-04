package com.jetbrains.python.sdk;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
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
    
    final VirtualFile path = getDefaultLocation();
    if (path != null)
      candidates.addAll(findInDirectory(path));

    return candidates;
  }

  @Nullable
  public static VirtualFile getDefaultLocation() {
    final String path = System.getenv().get("WORKON_HOME");
    if (path != null) return LocalFileSystem.getInstance().findFileByPath(path.replace('\\','/'));

    final VirtualFile userHome = LocalFileSystem.getInstance().findFileByPath(SystemProperties.getUserHome().replace('\\','/'));
    if (userHome != null) {
      return userHome.findChild(".virtualenvs");
    }
    return null;
  }

  public static Collection<String> findInDirectory(VirtualFile rootDir) {
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

  private static Collection<String> findInterpreter(VirtualFile dir) {
    List<String> candidates = new ArrayList<String>();
    for (VirtualFile child : dir.getChildren()) {
      if (!child.isDirectory()) {
        final String childName = child.getName();
        for (String name : NAMES) {
          if (SystemInfo.isWindows && childName.equals(name)) {
            candidates.add(child.getPath());
          }
          else if (childName.startsWith(name)) {
            if (!childName.endsWith("-config")) candidates.add(child.getPath());
            break;
          }
        }
      }
    }
    return candidates;
  }

  @Override
  public boolean isValidSdkPath(@NotNull File file) {
    if (!super.isValidSdkPath(file)) return false;
    File bin = file.getParentFile();
    if (bin != null) {
      File[] children = bin.listFiles();
      if (children != null) {
        for (File f : children) {
          //is it good enough to determine virtual env?
          if (f.getName().equals("activate_this.py")) {
            return true;
          }
        }
      }
    }
    return false;
  }
}
