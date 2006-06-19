/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectJdksModel;
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

  private boolean myFreeze = false;

  public ProjectJdkConfigurable(Project project, final ProjectJdksModel jdksModel) {
    myProject = project;
    myJdksModel = jdksModel;
    init();
  }

  public ProjectJdk getSelectedProjectJdk() {
    return myJdksModel.findSdk(myCbProjectJdk.getSelectedJdk());
  }

  public JComponent createComponent() {
    myFreeze = true;
    final ProjectJdk projectJdk = myCbProjectJdk.getSelectedJdk();
    myCbProjectJdk.reloadModel(new JdkComboBox.NoneJdkComboBoxItem(), myProject);
    myCbProjectJdk.setSelectedJdk(projectJdk); //restore selection
    myFreeze = false;
    return myJdkPanel;
  }

  private void init() {
    myJdkPanel = new JPanel(new GridBagLayout());
    myCbProjectJdk = new JdkComboBox(myJdksModel);
    myCbProjectJdk.insertItemAt(new JdkComboBox.NoneJdkComboBoxItem(), 0);
    myCbProjectJdk.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (myFreeze) return;
        myJdksModel.setProjectJdk(myCbProjectJdk.getSelectedJdk());
      }
    });
    final Box horizontalBox = Box.createHorizontalBox();
    horizontalBox.add(new JLabel(ProjectBundle.message("module.libraries.target.jdk.project.radio")));
    horizontalBox.add(Box.createHorizontalStrut(5));
    horizontalBox.add(myCbProjectJdk);
    myJdkPanel.add(horizontalBox, new GridBagConstraints(0, 1, 1, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(5, 6, 0, 0), 0, 0));
  }

  public boolean isModified() {
    final ProjectJdk projectJdk = ProjectRootManager.getInstance(myProject).getProjectJdk();
    return projectJdk == null || !Comparing.equal(projectJdk, getSelectedProjectJdk());
  }

  public void apply() throws ConfigurationException {
    ProjectRootManager.getInstance(myProject).setProjectJdk(getSelectedProjectJdk());
  }

  public void reset() {
    final ProjectJdk projectJdk = ProjectRootManager.getInstance(myProject).getProjectJdk();
    if (projectJdk != null) {
      final String sdkName = projectJdk.getName();
      myCbProjectJdk.setSelectedJdk((ProjectJdk)myJdksModel.findSdk(sdkName));
    } else {
      myCbProjectJdk.setSelectedJdk(null);
    }
  }

  public void disposeUIResources() {
    myJdkPanel = null;
    myCbProjectJdk = null;
  }

}
