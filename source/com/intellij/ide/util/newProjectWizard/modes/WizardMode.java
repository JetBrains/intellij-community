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

public abstract class WizardMode implements Disposable {
  public static final ExtensionPointName<WizardMode> MODES = ExtensionPointName.create("com.intellij.wizardMode");

  private StepSequence myStepSequence;

  @NotNull
  public abstract String getDisplayName(final WizardContext context);

  @NotNull
  public abstract String getDescription(final WizardContext context);

  public abstract boolean isAvailable(final WizardContext context);

  @Nullable
  public StepSequence getSteps(final WizardContext context, final ModulesProvider modulesProvider) {
    if (myStepSequence == null) {
      myStepSequence = createSteps(context, modulesProvider);
    }
    return myStepSequence;
  }

  @Nullable
  public abstract StepSequence createSteps(final WizardContext context, final ModulesProvider modulesProvider);

  @Nullable
  public abstract ProjectBuilder getModuleBuilder();

  @Nullable
  public abstract JComponent getAdditionalSettings();

  public abstract void onChosen(final boolean enabled);

  protected String getSelectedType() {
    return myStepSequence != null ? myStepSequence.getSelectedType() : null;
  }

  public void dispose() {
    myStepSequence = null;
  }
}