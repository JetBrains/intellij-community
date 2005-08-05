package com.intellij.ide.util.projectWizard;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ide.GeneralSettings;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 21, 2004
 */
public class ProjectNameStep extends ModuleWizardStep {
  private static final Icon NEW_PROJECT_ICON = IconLoader.getIcon("/newprojectwizard.png");
  private static final String PROJECT_FILE_EXTENSION = ".ipr";
  private NamePathComponent myNamePathComponent;
  private JPanel myPanel;
  private final WizardContext myWizardContext;

  public ProjectNameStep(WizardContext wizardContext) {
    myWizardContext = wizardContext;
    myNamePathComponent = new NamePathComponent("Name:", "Project file location:", 'a', 'l', "Select project file directory", "Project file will be stored in this directory");

    final String projectsStorePath = getDefaultProjectsStorePath();
    final String initialProjectName = ProjectWizardUtil.findNonExistingFileName(projectsStorePath, "untitled", "");
    myNamePathComponent.setPath(projectsStorePath + File.separator + initialProjectName);
    myNamePathComponent.setNameValue(initialProjectName);

    myPanel = new JPanel(new GridBagLayout());
    myPanel.setBorder(BorderFactory.createEtchedBorder());

    ApplicationInfo info = ApplicationManager.getApplication().getComponent(ApplicationInfo.class);
    String appName = info.getVersionName();
    myPanel.add(new JLabel("Please enter a project name to create a new " + appName + " project."), new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(8, 10, 8, 10), 0, 0));

    myPanel.add(myNamePathComponent, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(8, 10, 8, 10), 0, 0));
  }

  public JComponent getPreferredFocusedComponent() {
    return myNamePathComponent.getNameComponent();
  }

  public String getHelpId() {
    return "project.new.page1";
  }

  public JComponent getComponent() {
    return myPanel;
  }

  public void updateDataModel() {
    myWizardContext.setProjectName(getProjectName());
    myWizardContext.setProjectFileDirectory(getProjectFileDirectory());
  }

  public Icon getIcon() {
    return NEW_PROJECT_ICON;
  }

  private String getDefaultProjectsStorePath() {
    final String lastProjectLocation = GeneralSettings.getInstance().getLastProjectLocation();
    if (lastProjectLocation != null) {
      return lastProjectLocation.replace('/', File.separatorChar);
    }
    final String userHome = System.getProperty("user.home");
    return userHome.replace('/', File.separatorChar) + File.separator +
           ApplicationNamesInfo.getInstance().getLowercaseProductName() + "Projects";
  }


  public boolean validate() {
    String name = myNamePathComponent.getNameValue();
    if (name.length() == 0 ) {
      final ApplicationInfo info = ApplicationManager.getApplication().getComponent(ApplicationInfo.class);
      final String message = "Enter a file name to create a new " + info.getVersionName() + " project";
      Messages.showMessageDialog(myPanel, message, "Error", Messages.getErrorIcon());
      return false;
    }

    final String projectFileDirectory = getProjectFileDirectory();
    if (projectFileDirectory.length() == 0) {
      Messages.showMessageDialog(myPanel, "Enter project file location", "Error", Messages.getErrorIcon());
      return false;
    }

    final boolean shouldPromptCreation = myNamePathComponent.isPathChangedByUser();
    if (!ProjectWizardUtil.createDirectoryIfNotExists("The project file directory\n", projectFileDirectory, shouldPromptCreation)) {
      return false;
    }

    boolean shouldContinue = true;
    final File projectFile = new File(getProjectFilePath());
    if (projectFile.exists()) {
      int answer = Messages.showYesNoDialog(
        "The project file \n'" + projectFile.getAbsolutePath()+ "'\nalready exists.\nWould you like to overwrite it?",
        "File Already Exists",
        Messages.getQuestionIcon()
      );
      shouldContinue = (answer == 0);
    }

    return shouldContinue;
  }

  public String getProjectFilePath() {
    return getProjectFileDirectory() + "/" + myNamePathComponent.getNameValue()/*myTfProjectName.getText().trim()*/ + PROJECT_FILE_EXTENSION;
  }

  public String getProjectFileDirectory() {
    return myNamePathComponent.getPath();
  }

  public String getProjectName() {
    return myNamePathComponent.getNameValue();
  }

}
