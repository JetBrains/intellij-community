
package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiPackage;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.DataAccessors;

public final class FilePackageMacro extends Macro {
  public String getName() {
    return "FilePackage";
  }

  public String getDescription() {
    return IdeBundle.message("macro.file.package");
  }

  public String expand(DataContext dataContext) {
    PsiPackage aPackage = DataAccessors.FILE_PACKAGE.from(dataContext);
    if (aPackage == null) return null;
    return aPackage.getName();
  }
}
