package com.intellij.util.io.fs;

public class FileSystem {
  public static IFileSystem FILE_SYSTEM = new IoFileSystem();

  private FileSystem() {
  }
}
