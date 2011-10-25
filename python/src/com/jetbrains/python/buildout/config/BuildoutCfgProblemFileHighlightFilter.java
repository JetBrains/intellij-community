package com.jetbrains.python.buildout.config;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author traff
 */
public class BuildoutCfgProblemFileHighlightFilter implements Condition<VirtualFile> {
  @Override
  public boolean value(VirtualFile virtualFile) {
    final FileType fileType = virtualFile.getFileType();
    return fileType == BuildoutCfgFileType.INSTANCE;
  }
}
