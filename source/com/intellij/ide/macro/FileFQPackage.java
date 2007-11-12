package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiPackage;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.DataAccessors;

public class FileFQPackage extends Macro {
  public String expand(DataContext dataContext) throws Macro.ExecutionCancelledException {
    PsiPackage aPackage = DataAccessors.FILE_PACKAGE.from(dataContext);
    if (aPackage == null) return null;
    return aPackage.getQualifiedName();
  }

  public String getDescription() {
    return IdeBundle.message("macro.file.fully.qualified.package");
  }

  public String getName() {
    return "FileFQPackage";
  }
}
