/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModel;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectJdksModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectRootConfigurable;
import com.intellij.openapi.util.Comparing;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * User: anna
 * Date: 05-Jun-2006
 */
public class ProjectJdkConfigurable implements UnnamedConfigurable {
  private JdkComboBox myCbProjectJdk;
  private JPanel myJdkPanel;
  private Project myProject;
  private ProjectJdksModel myJdksModel;
  private SdkModel.Listener myListener = new SdkModel.Listener() {
    public void sdkAdded(Sdk sdk) {
      reloadModel();
    }

    public void beforeSdkRemove(Sdk sdk) {
      reloadModel();
    }

    public void sdkChanged(Sdk sdk, String previousName) {
      reloadModel();
    }

    public void sdkHomeSelected(Sdk sdk, String newSdkHome) {
      reloadModel();
    }
  };

  private boolean myFreeze = false;

  public ProjectJdkConfigurable(Project project, final ProjectJdksModel jdksModel) {
    myProject = project;
    myJdksModel = jdksModel;
    myJdksModel.addListener(myListener);
    init();
  }

  public ProjectJdk getSelectedProjectJdk() {
    return myJdksModel.findSdk(myCbProjectJdk.getSelectedJdk());
  }

  public JComponent createComponent() {
    return myJdkPanel;
  }

  private void reloadModel() {
    myFreeze = true;
    final ProjectJdk projectJdk = myJdksModel.getProjectJdk();
    myCbProjectJdk.reloadModel(new JdkComboBox.NoneJdkComboBoxItem(), myProject);
    myCbProjectJdk.setSelectedJdk(projectJdk); //restore selection
    myFreeze = false;
  }

  private void init() {
    myJdkPanel = new JPanel(new GridBagLayout());
    myCbProjectJdk = new JdkComboBox(myJdksModel);
    myCbProjectJdk.insertItemAt(new JdkComboBox.NoneJdkComboBoxItem(), 0);
    myCbProjectJdk.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (myFreeze) return;
        final ProjectJdk oldJdk = myJdksModel.getProjectJdk();
        myJdksModel.setProjectJdk(myCbProjectJdk.getSelectedJdk());
        final ProjectRootConfigurable rootConfigurable = ProjectRootConfigurable.getInstance(myProject);
        Module[] modules = rootConfigurable.getModules();
        for (Module module : modules) {
          rootConfigurable.clearCaches(module, oldJdk, getSelectedProjectJdk());
        }
      }
    });
    myJdkPanel.add(new JLabel(ProjectBundle.message("module.libraries.target.jdk.project.radio")), new GridBagConstraints(0, 0, 2, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(0, 0, 4, 0), 0, 0));
    myJdkPanel.add(myCbProjectJdk, new GridBagConstraints(0, 1, 1, 1, 0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(0, 4, 0, 0), 0, 0));
    final JButton setUpButton = myCbProjectJdk.createSetupButton(myProject, myJdksModel, new JdkComboBox.NoneJdkComboBoxItem());
    myJdkPanel.add(setUpButton, new GridBagConstraints(1, 1, 1, 1, 1.0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 4, 0, 0), 0, 0));
  }

  public boolean isModified() {
    final ProjectJdk projectJdk = ProjectRootManager.getInstance(myProject).getProjectJdk();
    return !Comparing.equal(projectJdk, getSelectedProjectJdk());
  }

  public void apply() throws ConfigurationException {
    ProjectRootManager.getInstance(myProject).setProjectJdk(getSelectedProjectJdk());
  }

  public void reset() {
    final String sdkName = ProjectRootManager.getInstance(myProject).getProjectJdkName();
    if (sdkName != null) {
      final ProjectJdk jdk = (ProjectJdk)myJdksModel.findSdk(sdkName);
      if (jdk != null) {
        myCbProjectJdk.setSelectedJdk(jdk);
      } else {
        myCbProjectJdk.setInvalidJdk(sdkName);
      }
    } else {
      myCbProjectJdk.setSelectedJdk(null);
    }
  }

  public void disposeUIResources() {
    myJdksModel.removeListener(myListener);
    myJdkPanel = null;
    myCbProjectJdk = null;
  }

}
