package com.jetbrains.python.remote;

import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class RemoteFile {

  private final String myPath;

  public RemoteFile(String path) {
    myPath = path;
  }

  public RemoteFile(String parent, String child) {
    String separator;

    if (isWindowsPath(parent)) {
      separator = "\\";
    }
    else {
      separator = "/";
    }

    if (parent.endsWith(separator)) {
      myPath = parent + child;
    }
    else {
      myPath = parent + separator + child;
    }
  }

  public String getPath() {
    return myPath;
  }

  public boolean isWin() {
    return isWindowsPath(myPath);
  }

  private static boolean isWindowsPath(@NotNull String path) {
    return path.contains("\\");
  }
}
