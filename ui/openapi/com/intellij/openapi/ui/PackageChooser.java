package com.intellij.openapi.ui;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiPackage;

import java.util.List;

/**
 * @author ven
 */
public abstract class PackageChooser extends DialogWrapper {
  public PackageChooser(Project project, boolean canBeParent) {
    super(project, canBeParent);
  }

  public abstract PsiPackage getSelectedPackage();

  public abstract List<PsiPackage> getSelectedPackages();

  public abstract void selectPackage(String qualifiedName);
}
