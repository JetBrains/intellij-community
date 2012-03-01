package com.jetbrains.python.remote;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class RemoteFile {

  private final String myPath;

  public RemoteFile(@NotNull String path, boolean isWin) {
    myPath = toSystemDependent(path, isWin);
  }

  public RemoteFile(@NotNull String parent, String child) {
    this(resolveChild(parent, child, isWindowsPath(parent)), isWindowsPath(parent));
  }

  private static String resolveChild(@NotNull String parent, @NotNull String child, boolean win) {
    String separator;
    if (win) {
      separator = "\\";
    }
    else {
      separator = "/";
    }

    String path;
    if (parent.endsWith(separator)) {
      path = parent + child;
    }
    else {
      path = parent + separator + child;
    }
    return path;
  }


  public String getPath() {
    return myPath;
  }

  public boolean isWin() {
    return isWindowsPath(myPath);
  }

  public static boolean isWindowsPath(@NotNull String path) {
    return path.contains("\\");
  }

  private static String toSystemDependent(@NotNull String path, boolean isWin) {
    char separator = isWin ? '\\' : '/';
    return FileUtil.toSystemIndependentName(path).replace('/', separator);
  }

  public static RemoteFileBuilder detectSystemByPath(@NotNull String path) {
    return new RemoteFileBuilder(isWindowsPath(path));
  }

  public static class RemoteFileBuilder {
    private final boolean isWin;

    private RemoteFileBuilder(boolean win) {
      isWin = win;
    }

    public RemoteFile createRemoteFile(String path) {
      return new RemoteFile(path, isWin);
    }
  }
}
