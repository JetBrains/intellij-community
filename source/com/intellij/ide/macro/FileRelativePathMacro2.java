
package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.DataContext;

import java.io.File;

public final class FileRelativePathMacro2 extends FileRelativePathMacro {
  public String getName() {
    return "/FileRelativePath";
  }

  public String getDescription() {
    return "File path relative to the project file (with forward slashes)";
  }

  public String expand(DataContext dataContext) {
    String s = super.expand(dataContext);
    return s != null ? s.replace(File.separatorChar, '/') : null;
  }
}
