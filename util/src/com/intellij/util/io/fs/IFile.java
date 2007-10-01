package com.intellij.util.io.fs;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface IFile {
  boolean exists();

  byte[] loadBytes() throws IOException;

  InputStream openInputStream() throws IOException;
  OutputStream openOutputStream() throws FileNotFoundException;

  boolean delete();

  void renameTo(final IFile newFile) throws IOException;

  void createParentDirs();

  IFile getParentFile();

  String getName();

  String getPath();

  String getCanonicalPath();
  String getAbsolutePath();

  long length();

  IFile getChild(final String childName);

  boolean isDirectory();

  IFile[] listFiles();

  void mkDir();

  long getTimeStamp();
}
