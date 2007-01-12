package com.intellij.util.io.fs;

public interface IFileSystem {
  IFile createFile(String filePath);
  char getSeparatorChar();
}
