package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiPackage;

public class FileFQPackage extends Macro {
  public String expand(DataContext dataContext) throws Macro.ExecutionCancelledException {
    PsiPackage aPackage = DataAccessor.FILE_PACKAGE.from(dataContext);
    if (aPackage == null) return null;
    return aPackage.getQualifiedName();
  }

  public String getDescription() {
    return "File fully qualified package";
  }

  public String getName() {
    return "FileFQPackage";
  }
}
