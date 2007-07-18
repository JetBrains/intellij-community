/*
 * User: anna
 * Date: 10-Jul-2007
 */
package com.intellij.ide.util.newProjectWizard.modes;

import com.intellij.ide.util.newProjectWizard.ProjectFromSourcesBuilder;
import com.intellij.ide.util.newProjectWizard.ProjectNameStep;
import com.intellij.ide.util.newProjectWizard.SourcePathsStep;
import com.intellij.ide.util.newProjectWizard.StepSequence;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class CreateFromSourcesMode extends WizardMode {
  private ProjectFromSourcesBuilder myProjectBuilder;

  @NotNull
  public String getDisplayName(final WizardContext context) {
    return ProjectBundle.message("project.new.wizard.from.existent.sources.title", context.getPresentationName());
  }

  @NotNull
  public String getDescription(final WizardContext context) {
    return ProjectBundle.message("project.new.wizard.from.existent.sources.description", context.getPresentationName());
  }

  @Nullable
  protected StepSequence createSteps(final WizardContext context, final ModulesProvider modulesProvider) {
    myProjectBuilder = new ProjectFromSourcesBuilder();
    final StepSequence sequence = new StepSequence();
    final ProjectFromSourcesBuilder projectBuilder = new ProjectFromSourcesBuilder();
    sequence.addCommonStep(new ProjectNameStep(context, sequence, this));
    sequence.addCommonStep(new SourcePathsStep(context, projectBuilder, null, null));
    
    return sequence;
  }

  public boolean isAvailable(WizardContext context) {
    return true;
  }

  public ProjectBuilder getModuleBuilder() {
    return myProjectBuilder;
  }

  @Nullable
  public JComponent getAdditionalSettings() {
    return null;
  }

  public void onChosen(final boolean enabled) {

  }

  public void dispose() {
    myProjectBuilder = null;
    super.dispose();
  }
}