
package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.DataContext;

import java.io.File;

public final class FileDirRelativeToSourcepathMacro2 extends FileDirRelativeToSourcepathMacro {
  public String getName() {
    return "/FileDirRelativeToSourcepath";
  }

  public String getDescription() {
    return "File dir relative to the sourcepath the file belongs to (with forward slashes)";
  }

  public String expand(DataContext dataContext) {
    String s = super.expand(dataContext);
    return s != null ? s.replace(File.separatorChar, '/') : null;
  }
}
