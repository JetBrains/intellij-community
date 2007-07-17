/*
 * User: anna
 * Date: 09-Jul-2007
 */
package com.intellij.ide.util.newProjectWizard;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.util.newProjectWizard.modes.WizardMode;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.ide.wizard.AbstractWizard;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AddModuleWizard extends AbstractWizard<ModuleWizardStep> {
  private static final String ADD_MODULE_TITLE = IdeBundle.message("title.add.module");
  private static final String NEW_PROJECT_TITLE = IdeBundle.message("title.new.project");
  private Project myCurrentProject;
  private WizardContext myWizardContext;
  private ProjectCreateModeStep myRootStep;


  /**
   * @param project if null, the wizard will start creating new project, otherwise will add a new module to the existing proj.
   */
  public AddModuleWizard(final Project project, final ModulesProvider modulesProvider, @Nullable String defaultPath) {
    super(project == null ? NEW_PROJECT_TITLE : ADD_MODULE_TITLE, project);
    myCurrentProject = project;
    initModuleWizard(project, modulesProvider, defaultPath);
  }

  /**
   * @param project if null, the wizard will start creating new project, otherwise will add a new module to the existing proj.
   */
  public AddModuleWizard(Component parent, final Project project, ModulesProvider modulesProvider) {
    super(project == null ? NEW_PROJECT_TITLE : ADD_MODULE_TITLE, parent);
    myCurrentProject = project;
    initModuleWizard(project, modulesProvider, null);
  }

  private void initModuleWizard(final Project project, final ModulesProvider modulesProvider, @Nullable final String defaultPath) {
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        ProjectManager.getInstance().getDefaultProject(); //warm up components
      }
    }, ProjectBundle.message("project.new.wizard.progress.title"), true, null);
    myWizardContext = new WizardContext(project);
    if (defaultPath != null) {
      myWizardContext.setProjectFileDirectory(defaultPath);
    }
    myWizardContext.addContextListener(new WizardContext.Listener() {
      public void buttonsUpdateRequested() {
        updateButtons();
      }
    });

    final ArrayList<WizardMode> modes = new ArrayList<WizardMode>();
    for (WizardMode mode : Extensions.getExtensions(WizardMode.MODES)) {
      if (mode.isAvailable(myWizardContext)) {
        modes.add(mode);
      }
    }
    myRootStep = new ProjectCreateModeStep(modes, myWizardContext){
      protected void update() {
        updateButtons();
      }
    };
    addStep(myRootStep);
    for (WizardMode mode : modes) {
      appendSteps(mode.getSteps(myWizardContext, modulesProvider));
    }
    init();
  }

  private void appendSteps(@Nullable final StepSequence sequence) {
    if (sequence != null) {
      final List<ModuleWizardStep> commonSteps = sequence.getCommonSteps();
      for (ModuleWizardStep step : commonSteps) {
        addStep(step);
      }
      for (String type : sequence.getTypes()) {
        appendSteps(sequence.getSpecificSteps(type));
      }
    }
  }


  protected void updateStep() {
    final ModuleWizardStep currentStep = getCurrentStepObject();
    currentStep.updateStep();

    super.updateStep();

    updateButtons();

    final JButton nextButton = getNextButton();
    final JButton finishButton = getFinishButton();
    final boolean isLastStep = isLastStep(getCurrentStep());

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        if (!isShowing()) {
          return;
        }
        final JComponent preferredFocusedComponent = currentStep.getPreferredFocusedComponent();
        if (preferredFocusedComponent != null) {
          preferredFocusedComponent.requestFocus();
        }
        else {
          if (isLastStep) {
            finishButton.requestFocus();
          }
          else {
            nextButton.requestFocus();
          }
        }
        getRootPane().setDefaultButton(isLastStep ? finishButton : nextButton);
      }
    });
  }

  protected void dispose() {
    for (ModuleWizardStep step : mySteps) {
      step.disposeUIResources();
    }
    super.dispose();
  }

  protected final void doOKAction() {
    int idx = getCurrentStep();
    try {
      do {
        final ModuleWizardStep step = mySteps.get(idx);
        step.updateStep();
        if (!step.validate()) {
          return;
        }
        if (!commitStepData(step)) {
          return;
        }
        step.onStepLeaving();
        try {
          step._commit(true);
        }
        catch (CommitStepException e) {
          Messages.showErrorDialog(getCurrentStepComponent(), e.getMessage());
          return;
        }
        if (!validateDataOnClose()) {
          return;
        }
        if (!isLastStep(idx)) {
          idx = getNextStep(idx);
        } else {
          break;
        }
      } while (true);
    }
    catch (ConfigurationException e) {
      Messages.showErrorDialog(myCurrentProject, e.getMessage(), e.getTitle());
    }
    finally {
      myCurrentStep = idx;
      updateStep();
    }
    super.doOKAction();
  }

  private boolean commitStepData(final ModuleWizardStep step) {
    try {
      if (!step.validate()) {
        return false;
      }
    }
    catch (ConfigurationException e) {
      Messages.showErrorDialog(myCurrentProject, e.getMessage(), e.getTitle());
    }
    step.updateDataModel();
    return true;
  }

  private boolean validateDataOnClose() {
    return true;
    /*final ProjectBuilder projectBuilder = getProjectBuilder();
    return projectBuilder == null || projectBuilder.validate();*/

    /*if (myModuleTypeStep.isCreateNewModule()) {
      final File file = new File(projectBuilder.getModuleFilePath());
      if (file.exists()) {
        final int answer = Messages.showOkCancelDialog(
          IdeBundle.message("prompt.module.file.overwrite", file.getPath()),
          IdeBundle.message("title.file.already.exists"), Messages.getQuestionIcon());
        if (answer != 0) {
          return false;
        }
      }
    }
    else if (myModuleTypeStep.isImportExistingModule()) {
      final File file = new File(projectBuilder.getModuleFilePath());
      if (file.exists()) {
        try {
          final Document document = JDOMUtil.loadDocument(file);
          final Element root = document.getRootElement();
          if (!convertModule(file, document, root)) {
            return false;
          }
          final Set<String> usedMacros = PathMacrosCollector.getMacroNames(root);
          final Set<String> definedMacros = PathMacros.getInstance().getAllMacroNames();
          usedMacros.remove("$" + PathMacrosImpl.MODULE_DIR_MACRO_NAME + "$");
          usedMacros.removeAll(definedMacros);
          if (usedMacros.size() > 0) {
            final boolean ok = ProjectManagerImpl.showMacrosConfigurationDialog(myCurrentProject, usedMacros);
            if (!ok) {
              return false;
            }
          }
        }
        catch (JDOMException e) {
          Messages.showMessageDialog(e.getMessage(), IdeBundle.message("title.error.reading.file"), Messages.getErrorIcon());
          return false;
        }
        catch (IOException e) {
          Messages.showMessageDialog(e.getMessage(), IdeBundle.message("title.error.reading.file"), Messages.getErrorIcon());
          return false;
        }
      }
    }*/
  }

  protected void doNextAction() {
    final ModuleWizardStep step = getCurrentStepObject();
    if (!commitStepData(step)) {
      return;
    }
    step.onStepLeaving();
    super.doNextAction();
  }

  protected void doPreviousAction() {
    final ModuleWizardStep step = getCurrentStepObject();
    step.onStepLeaving();
    super.doPreviousAction();
  }

  public void doCancelAction() {
    final ModuleWizardStep step = getCurrentStepObject();
    step.onStepLeaving();
    super.doCancelAction();
  }

  private void updateButtons() {
    final boolean isLastStep = isLastStep(getCurrentStep());
    getNextButton().setEnabled(!isLastStep);
    getFinishButton().setEnabled(isLastStep);
  }

  private boolean isLastStep(int step) {
    return getNextStep(step) == step;
  }


  protected String getHelpID() {
    ModuleWizardStep step = getCurrentStepObject();
    if (step != null) {
      return step.getHelpId();
    }
    return null;
  }

  protected final int getNextStep(int step) {
    ModuleWizardStep nextStep = null;
    final StepSequence stepSequence = getMode().getSteps(myWizardContext, null);
    if (stepSequence != null) {
      if (myRootStep == mySteps.get(step)) {
        return mySteps.indexOf(stepSequence.getCommonSteps().get(0));
      }
      nextStep = stepSequence.getNextStep(mySteps.get(step));
      while (nextStep != null && !nextStep.isStepVisible()) {
        nextStep = stepSequence.getNextStep(nextStep);
      }
    }
    return nextStep == null ? step : mySteps.indexOf(nextStep);
  }

  protected final int getPreviousStep(final int step) {
    ModuleWizardStep previousStep = null;
    final StepSequence stepSequence = getMode().getSteps(myWizardContext, null);
    if (stepSequence != null) {
      previousStep = stepSequence.getPreviousStep(mySteps.get(step));
      while (previousStep != null && !previousStep.isStepVisible()) {
        previousStep = stepSequence.getPreviousStep(previousStep);
      }
    }
    return previousStep == null ? 0 : mySteps.indexOf(previousStep);
  }

  private WizardMode getMode() {
    return myRootStep.getMode();
  }

  @NotNull
  public String getNewProjectFilePath() {
    return myWizardContext.getProjectFileDirectory() + File.separator + myWizardContext.getProjectName() + ProjectFileType.DOT_DEFAULT_EXTENSION;
  }

  @Nullable
  public static ProjectJdk getNewProjectJdk() {
    final Project defaultProject = ProjectManager.getInstance().getDefaultProject();
    return ProjectRootManager.getInstance(defaultProject).getProjectJdk();
  }

  @NotNull
  public String getNewCompileOutput() {
    final String projectFilePath = myWizardContext.getProjectFileDirectory();
    @NonNls String path = myWizardContext.getCompilerOutputDirectory();
    if (path == null) {
      path = StringUtil.endsWithChar(projectFilePath, '/') ? projectFilePath + "classes" : projectFilePath + "/classes";
    }
    return path;
  }

  @NonNls
  public String getModuleFilePath() {
    return myWizardContext.getProjectFileDirectory() + File.separator + myWizardContext.getProjectName() + ".iml";
  }

  public ProjectBuilder getProjectBuilder() {
    return myWizardContext.getProjectBuilder();
  }

  public String getProjectName() {
    return myWizardContext.getProjectName();
  }
}