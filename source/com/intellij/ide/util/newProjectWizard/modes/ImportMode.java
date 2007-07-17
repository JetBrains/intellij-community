/*
 * User: anna
 * Date: 10-Jul-2007
 */
package com.intellij.ide.util.newProjectWizard.modes;

import com.intellij.ide.util.newProjectWizard.StepSequence;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.projectImport.ImportChooserStep;
import com.intellij.projectImport.ProjectImportProvider;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;

public class ImportMode extends WizardMode {
  @NotNull
  public String getDisplayName(final WizardContext context) {
    return ProjectBundle.message("project.new.wizard.import.title", context.getPresentationName());
  }

  @NotNull
  public String getDescription(final WizardContext context) {
    final String productName = ApplicationNamesInfo.getInstance().getProductName();
    return ProjectBundle.message("project.new.wizard.import.description", productName, context.getPresentationName(), StringUtil.join(
      Arrays.asList(Extensions.getExtensions(ProjectImportProvider.PROJECT_IMPORT_PROVIDER)),
      new Function<ProjectImportProvider, String>() {
        public String fun(final ProjectImportProvider provider) {
          return provider.getName();
        }
      }, ", "));
  }

  @Nullable
  public StepSequence createSteps(final WizardContext context, final ModulesProvider modulesProvider) {
    final StepSequence stepSequence = new StepSequence(null);
    final ProjectImportProvider[] providers = Extensions.getExtensions(ProjectImportProvider.PROJECT_IMPORT_PROVIDER);
    stepSequence.addCommonStep(new ImportChooserStep(providers, stepSequence, context));
    for (ProjectImportProvider provider : providers) {
      final ModuleWizardStep[] steps = provider.createSteps(context);
      final StepSequence sequence = new StepSequence(stepSequence);
      for (ModuleWizardStep step : steps) {
        sequence.addCommonStep(step);
      }
      stepSequence.addSpecificSteps(provider.getId(), sequence);
    }
    return stepSequence;
  }

  public boolean isAvailable(WizardContext context) {
    return true;
  }

  @Nullable
  public ProjectBuilder getModuleBuilder() {
    return null;
  }

  @Nullable
  public JComponent getAdditionalSettings() {
    return null;
  }

  public void onChosen(final boolean enabled) {}

  public void dispose() {}
}