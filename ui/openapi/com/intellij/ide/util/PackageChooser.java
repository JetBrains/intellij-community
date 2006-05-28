package com.intellij.ide.util;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiPackage;

/**
 * @author ven
 */
public abstract class PackageChooser extends DialogWrapper {
  public PackageChooser(Project project, boolean canBeParent) {
    super(project, canBeParent);
  }

  public abstract PsiPackage getSelectedPackage();
}
