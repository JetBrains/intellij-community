
package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiPackage;
import com.intellij.ide.IdeBundle;

public final class FilePackageMacro extends Macro {
  public String getName() {
    return "FilePackage";
  }

  public String getDescription() {
    return IdeBundle.message("macro.file.package");
  }

  public String expand(DataContext dataContext) {
    PsiPackage aPackage = DataAccessor.FILE_PACKAGE.from(dataContext);
    if (aPackage == null) return null;
    return aPackage.getName();
  }
}
