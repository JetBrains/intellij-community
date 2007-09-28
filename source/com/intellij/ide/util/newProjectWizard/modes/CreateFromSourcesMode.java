/*
 * User: anna
 * Date: 10-Jul-2007
 */
package com.intellij.ide.util.newProjectWizard.modes;

import com.intellij.ide.util.importProject.*;
import com.intellij.ide.util.newProjectWizard.ProjectFromSourcesBuilder;
import com.intellij.ide.util.newProjectWizard.ProjectNameStep;
import com.intellij.ide.util.newProjectWizard.StepSequence;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.ide.util.projectWizard.ProjectWizardStepFactory;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class CreateFromSourcesMode extends WizardMode {
  private static final Icon NEW_PROJECT_ICON = IconLoader.getIcon("/newprojectwizard.png");
  private static final Icon ICON = IconLoader.getIcon("/addmodulewizard.png");
  private ProjectFromSourcesBuilder myProjectBuilder;

  @NotNull
  public String getDisplayName(final WizardContext context) {
    return ProjectBundle.message("project.new.wizard.from.existent.sources.title", context.getPresentationName());
  }

  @NotNull
  public String getDescription(final WizardContext context) {
    return ProjectBundle.message("project.new.wizard.from.existent.sources.description",
                                 ApplicationNamesInfo.getInstance().getProductName(), context.getPresentationName());
  }

  @Nullable
  protected StepSequence createSteps(final WizardContext context, final ModulesProvider modulesProvider) {
    final ModuleInsight moduleInsight = new ModuleInsight(new DelegatingProgressIndicator());
    final ProjectFromSourcesBuilder projectBuilder = new ProjectFromSourcesBuilder(moduleInsight);
    myProjectBuilder = projectBuilder;
    
    final ProjectWizardStepFactory factory = ProjectWizardStepFactory.getInstance();
    final StepSequence sequence = new StepSequence();
    final Icon icon = context.isCreatingNewProject() ? NEW_PROJECT_ICON : ICON;
    sequence.addCommonStep(new ProjectNameStep(context, sequence, this));
    sequence.addCommonStep(factory.createSourcePathsStep(context, projectBuilder, icon, "reference.dialogs.new.project.fromCode.source"));
    sequence.addCommonStep(new LibrariesDetectionStep(projectBuilder, moduleInsight, icon, "reference.dialogs.new.project.fromCode.page1"));
    sequence.addCommonStep(new ModulesDetectionStep(projectBuilder, moduleInsight, icon, "reference.dialogs.new.project.fromCode.page2"));
    sequence.addCommonStep(factory.createProjectJdkStep(context));
    sequence.addCommonStep(new FacetDetectionStep(projectBuilder, icon));
    return sequence;
  }

  public boolean isAvailable(WizardContext context) {
    return context.getProject() == null;
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