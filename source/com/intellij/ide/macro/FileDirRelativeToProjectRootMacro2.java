
package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.DataContext;

import java.io.File;

public final class FileDirRelativeToProjectRootMacro2 extends FileDirRelativeToProjectRootMacro {
  public String getName() {
    return "/FileDirRelativeToProjectRoot";
  }

  public String getDescription() {
    return "File dir relative to the module content root the file belongs to (with forward slashes)";
  }

  public String expand(DataContext dataContext) {
    String s = super.expand(dataContext);
    return s != null ? s.replace(File.separatorChar, '/') : null;
  }
}
