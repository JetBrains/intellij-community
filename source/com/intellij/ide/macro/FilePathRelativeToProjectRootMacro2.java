
package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.DataContext;

import java.io.File;

public final class FilePathRelativeToProjectRootMacro2 extends FilePathRelativeToProjectRootMacro {
  public String getName() {
    return "/FilePathRelativeToProjectRoot";
  }

  public String getDescription() {
    return "File path relative to the module content root the file belongs to (with forward slashes)";
  }

  public String expand(DataContext dataContext) {
    String s = super.expand(dataContext);
    return s != null ? s.replace(File.separatorChar, '/') : null;
  }
}
