/*
 * User: anna
 * Date: 18-Feb-2008
 */
package com.jetbrains.python.configuration;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.io.FileUtil;
import com.jetbrains.python.sdk.PythonSdkType;

import javax.swing.*;

public class PythonSdkConfigurable implements Configurable {
  private static final Icon ICON = IconLoader.getIcon("/modules/modules.png");
  private ProjectRootManager myProjectRootManager;
  private JTextField mySdkHomeTF = new JTextField();
  private Project myProject;

  public PythonSdkConfigurable(final ProjectRootManager projectRootManager, final Project project) {
    myProjectRootManager = projectRootManager;
    myProject = project;
  }

  public String getDisplayName() {
    return "Project Structure";
  }

  public Icon getIcon() {
    return ICON;
  }

  public String getHelpTopic() {
    return null;
  }

  public JComponent createComponent() {
    return mySdkHomeTF;
  }

  public boolean isModified() {
    final Sdk projectJdk = myProjectRootManager.getProjectJdk();
    if (projectJdk == null) return true;
    return !Comparing.strEqual(FileUtil.toSystemIndependentName(projectJdk.getHomePath()), FileUtil.toSystemIndependentName(mySdkHomeTF.getText()));
  }

  public void apply() throws ConfigurationException {
    PythonSdkType type = null;
    for (SdkType sdkType : Extensions.getExtensions(SdkType.EP_NAME)) {
      if (sdkType instanceof PythonSdkType) {
        type = (PythonSdkType)sdkType;
      }
    }
    assert type != null;
    if (!type.isValidSdkHome(mySdkHomeTF.getText())) {
      throw new ConfigurationException("invalid sdk home");
    }
    final PythonSdkType pythonSdkType = type;
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run(){
          final ProjectJdkImpl projectJdk = new ProjectJdkImpl("p", pythonSdkType);
          projectJdk.setHomePath(mySdkHomeTF.getText());
          pythonSdkType.setupSdkPaths(projectJdk);
          ProjectJdkTable.getInstance().addJdk(projectJdk);
          myProjectRootManager.setProjectJdk(projectJdk);
          final ModifiableRootModel model =
              ModuleRootManager.getInstance(ModuleManager.getInstance(myProject).getModules()[0]).getModifiableModel();
          model.inheritSdk();
          model.commit();
        }
    });

  }

  public void reset() {
    final Sdk sdk = myProjectRootManager.getProjectJdk();
    if (sdk != null) {
      mySdkHomeTF.setText(sdk.getHomePath());
    }
  }

  public void disposeUIResources() {

  }
}