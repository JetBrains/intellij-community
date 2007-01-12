package com.intellij.util.io.fs;

import java.io.IOException;

public interface IFile {
  boolean exists();

  byte[] loadBytes() throws IOException;

  boolean delete();

  void renameTo(final IFile newFile) throws IOException;

  void createParentDirs();

  IFile getParentFile();

  String getName();

  String getPath();

  String getCanonicalPath();
}
