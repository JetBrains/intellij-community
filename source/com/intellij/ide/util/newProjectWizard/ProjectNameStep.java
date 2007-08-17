package com.intellij.ide.util.newProjectWizard;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.newProjectWizard.modes.WizardMode;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.NamePathComponent;
import com.intellij.ide.util.projectWizard.ProjectWizardUtil;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * @author Eugene Zhuravlev
 *         Date: Jul 17, 2007
 */
public class ProjectNameStep extends ModuleWizardStep {
  private static final Icon NEW_PROJECT_ICON = IconLoader.getIcon("/newprojectwizard.png");
  @NonNls private static final String PROJECT_FILE_EXTENSION = ".ipr";
  @NonNls private static final String MODULE_FILE_EXTENSION = ".iml";

  private final JPanel myPanel;
  protected final JPanel myAdditionalContentPanel;
  protected NamePathComponent myNamePathComponent;
  protected final WizardContext myWizardContext;
  protected final StepSequence mySequence;
  protected final WizardMode myMode;
  
  public ProjectNameStep(WizardContext wizardContext, StepSequence sequence, final WizardMode mode) {
    myWizardContext = wizardContext;
    mySequence = sequence;
    myMode = mode;
    myNamePathComponent = new NamePathComponent(
      IdeBundle.message("label.project.name"),
      IdeBundle.message("label.component.file.location", StringUtil.capitalize(wizardContext.getPresentationName())),
      'a', 'l', IdeBundle.message("title.select.project.file.directory", wizardContext.getPresentationName()),
      IdeBundle.message("description.select.project.file.directory", StringUtil.capitalize(wizardContext.getPresentationName()))
    );
    //noinspection HardCodedStringLiteral
    final String baseDir = myWizardContext.getProject() == null
                           ? myWizardContext.getProjectFileDirectory()
                           : FileUtil.toSystemDependentName(myWizardContext.getProject().getBaseDir().getPath());
    final String initialProjectName = ProjectWizardUtil.findNonExistingFileName(baseDir, "untitled", "");
    myNamePathComponent.setPath(baseDir + File.separator + initialProjectName);
    myNamePathComponent.setNameValue(initialProjectName);
    myNamePathComponent.getNameComponent().setSelectionStart(0);
    myNamePathComponent.getNameComponent().setSelectionEnd(initialProjectName.length());

    myPanel = new JPanel(new GridBagLayout());
    myPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEtchedBorder(), BorderFactory.createEmptyBorder(10, 10, 10, 10)));
    final ApplicationInfo info = ApplicationManager.getApplication().getComponent(ApplicationInfo.class);
    String appName = info.getVersionName();
    final JLabel promptLabel = new JLabel(
      IdeBundle.message("label.please.enter.project.name", appName, wizardContext.getPresentationName())
    );
    myPanel.add(promptLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(8, 0, 8, 0), 0, 0));
    myPanel.add(myNamePathComponent, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(8, 0, 8, 0), 0, 0));
    myAdditionalContentPanel = new JPanel(new GridBagLayout());
    myPanel.add(myAdditionalContentPanel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(8, 0, 8, 0), 0, 0));
  }
  
  public JComponent getComponent() {
    return myPanel;
  }

  public void updateDataModel() {
    myWizardContext.setProjectName(getProjectName());
    myWizardContext.setProjectFileDirectory(getProjectFileDirectory());
    myWizardContext.setProjectBuilder(myMode.getModuleBuilder());
  }

  public Icon getIcon() {
    return myWizardContext.getProject() == null ? NEW_PROJECT_ICON : ICON;
  }

  public JComponent getPreferredFocusedComponent() {
    return myNamePathComponent.getNameComponent();
  }

  public String getHelpId() {
    return "reference.dialogs.new.project.fromCode.name";
  }

  public String getProjectFilePath() {
    return getProjectFileDirectory() + "/" + myNamePathComponent.getNameValue()/*myTfProjectName.getText().trim()*/ +
           (myWizardContext.getProject() == null ? PROJECT_FILE_EXTENSION : MODULE_FILE_EXTENSION);
  }

  public String getProjectFileDirectory() {
    return myNamePathComponent.getPath();
  }

  public String getProjectName() {
    return myNamePathComponent.getNameValue();
  }

  public boolean validate() throws ConfigurationException {
     final String name = myNamePathComponent.getNameValue();
     if (name.length() == 0) {
       final ApplicationInfo info = ApplicationManager.getApplication().getComponent(ApplicationInfo.class);
       throw new ConfigurationException(IdeBundle.message("prompt.new.project.file.name", info.getVersionName(), myWizardContext.getPresentationName()));
     }

     final String projectFileDirectory = getProjectFileDirectory();
     if (projectFileDirectory.length() == 0) {
       throw new ConfigurationException(IdeBundle.message("prompt.enter.project.file.location", myWizardContext.getPresentationName()));
     }

     final boolean shouldPromptCreation = myNamePathComponent.isPathChangedByUser();
     if (!ProjectWizardUtil
       .createDirectoryIfNotExists(IdeBundle.message("directory.project.file.directory", myWizardContext.getPresentationName()), projectFileDirectory, shouldPromptCreation)) {
       return false;
     }

     boolean shouldContinue = true;
     final File projectFile = new File(getProjectFilePath());
     if (projectFile.exists()) {
       int answer = Messages.showYesNoDialog(IdeBundle.message("prompt.overwrite.project.file", projectFile.getAbsolutePath(), myWizardContext.getPresentationName()),
                                             IdeBundle.message("title.file.already.exists"), Messages.getQuestionIcon());
       shouldContinue = (answer == 0);
     }

     return shouldContinue;
   }
}
