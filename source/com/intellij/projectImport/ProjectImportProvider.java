/*
 * User: anna
 * Date: 10-Jul-2007
 */
package com.intellij.projectImport;

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class ProjectImportProvider {
  public static final ExtensionPointName<ProjectImportProvider> PROJECT_IMPORT_PROVIDER = ExtensionPointName.create("com.intellij.projectImportProvider");

  private ProjectImportBuilder myBuilder;

  protected ProjectImportProvider(final ProjectImportBuilder builder) {
    myBuilder = builder;
  }

  public ProjectImportBuilder getBuilder() {
    return myBuilder;
  }

  @NonNls @NotNull
  public String getId(){
    return getBuilder().getName();
  }

  @NotNull
  public String getName(){
    return getBuilder().getName();
  }

  @Nullable
  public Icon getIcon() {
    return getBuilder().getIcon();
  }

  public abstract ModuleWizardStep[] createSteps(WizardContext context);
}