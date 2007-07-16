/*
 * User: anna
 * Date: 08-Jul-2007
 */
package com.intellij.ide.util.newProjectWizard.modes;

import com.intellij.ide.util.newProjectWizard.StepSequence;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface WizardMode extends Disposable {
  ExtensionPointName<WizardMode> MODES = ExtensionPointName.create("com.intellij.wizardMode");

  @NotNull
  String getDisplayName(final WizardContext context);

  @NotNull
  String getDescription(final WizardContext context);

  boolean isAvailable(final WizardContext context);

  @Nullable
  StepSequence getSteps(final WizardContext context, final ModulesProvider modulesProvider);

  @Nullable
  ProjectBuilder getModuleBuilder();

  @Nullable
  JComponent getAdditionalSettings();

  void onChosen(final boolean enabled);
}