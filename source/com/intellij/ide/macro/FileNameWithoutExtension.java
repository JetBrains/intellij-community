package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.vfs.VirtualFile;


public final class FileNameWithoutExtension extends FileNameMacro {
  public String getName() {
    return "FileNameWithoutExtension";
  }

  public String getDescription() {
    return "File name without extension";
  }

  public String expand(DataContext dataContext) {
    VirtualFile file = (VirtualFile)dataContext.getData(DataConstantsEx.VIRTUAL_FILE);
    if (file == null) return null;
    return file.getNameWithoutExtension();
  }
}