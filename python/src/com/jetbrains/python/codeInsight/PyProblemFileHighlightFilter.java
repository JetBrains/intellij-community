package com.jetbrains.python.codeInsight;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.PythonFileType;

/**
 * @author yole
 */
public class PyProblemFileHighlightFilter implements Condition<VirtualFile> {
  @Override
  public boolean value(VirtualFile virtualFile) {
    final FileType fileType = virtualFile.getFileType();
    return fileType == PythonFileType.INSTANCE;
  }
}
