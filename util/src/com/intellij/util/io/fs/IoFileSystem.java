package com.intellij.util.io.fs;

import java.io.File;

class IoFileSystem implements IFileSystem {
  public IFile createFile(String filePath) {
    return new IoFile(new File(filePath));
  }

  public char getSeparatorChar() {
    return File.separatorChar;
  }
}
