
package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.DataContext;

import java.io.File;

public final class FilePathRelativeToSourcepathMacro2 extends FilePathRelativeToSourcepathMacro {
  public String getName() {
    return "/FilePathRelativeToSourcepath";
  }

  public String getDescription() {
    return "File path relative to the sourcepath the file belongs to (with forward slashes)";
  }

  public String expand(DataContext dataContext) {
    String s = super.expand(dataContext);
    return s != null ? s.replace(File.separatorChar, '/') : null;
  }
}
