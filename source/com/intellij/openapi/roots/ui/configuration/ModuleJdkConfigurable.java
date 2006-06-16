/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
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
public class ModuleJdkConfigurable implements UnnamedConfigurable {
  private JdkComboBox myCbModuleJdk;
  private ProjectJdk mySelectedModuleJdk = null;
  private ModifiableRootModel myRootModel;
  private JPanel myJdkPanel;
  private ModuleEditor myModuleEditor;
  private ProjectRootConfigurable myProjectRootConfigurable;
  private boolean myFreeze = false;

  public ModuleJdkConfigurable(ModuleEditor moduleEditor,
                               ModifiableRootModel model,
                               ProjectRootConfigurable projectRootConfigurable) {
    myModuleEditor = moduleEditor;
    myRootModel = model;
    myProjectRootConfigurable = projectRootConfigurable;
    init();
  }

  /**
   * @return null if JDK should be inherited
   */
  public ProjectJdk getSelectedModuleJdk() {
    return myProjectRootConfigurable.getProjectJdksModel().findSdk(mySelectedModuleJdk);
  }

  public boolean isInheritJdk() {
    return myCbModuleJdk.getSelectedItem() instanceof JdkComboBox.ProjectJdkComboBoxItem;
  }

  public JComponent createComponent() {
    myFreeze = true;
    final ProjectJdk projectJdk = myCbModuleJdk.getSelectedJdk();
    myCbModuleJdk.reloadModel(new JdkComboBox.ProjectJdkComboBoxItem(), myProjectRootConfigurable);
    myCbModuleJdk.setSelectedJdk(projectJdk);    //restore selection
    myFreeze = false;
    return myJdkPanel;
  }

  private void init() {
    myJdkPanel = new JPanel(new GridBagLayout());
    myCbModuleJdk = new JdkComboBox(myProjectRootConfigurable);
    myCbModuleJdk.insertItemAt(new JdkComboBox.ProjectJdkComboBoxItem(), 0);
    myCbModuleJdk.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (myFreeze) return;
        mySelectedModuleJdk = myCbModuleJdk.getSelectedJdk();
        final ProjectJdk selectedModuleJdk = getSelectedModuleJdk();
        if (selectedModuleJdk != null) {
          myRootModel.setJdk(selectedModuleJdk);
        } else {
          myRootModel.inheritJdk();
        }
        myModuleEditor.updateOrderEntriesInEditors();
      }
    });
    myJdkPanel.add(new JLabel(ProjectBundle.message("module.libraries.target.jdk.module.radio")), new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(0, 6, 0, 0), 0, 0));
    myJdkPanel.add(myCbModuleJdk, new GridBagConstraints(0, 1, 1, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(0, 6, 0, 0), 0, 0));
  }

  @SuppressWarnings({"SimplifiableIfStatement"})
  public boolean isModified() {
    final ModuleRootManager rootManager = ModuleRootManager.getInstance(myRootModel.getModule());
    if (rootManager.isJdkInherited() != myRootModel.isJdkInherited()) return true;
    return !myRootModel.isJdkInherited() && !Comparing.equal(rootManager.getJdk(), myRootModel.getJdk());
  }

  public void apply() throws ConfigurationException {
    //do nothing
  }

  public void reset() {
    myFreeze = true;
    final ProjectJdk projectJdk = myRootModel.getJdk();
    if (projectJdk != null && !myRootModel.isJdkInherited()) {
      mySelectedModuleJdk = (ProjectJdk)myProjectRootConfigurable.getProjectJdksModel().findSdk(projectJdk.getName());
      myCbModuleJdk.setSelectedJdk(mySelectedModuleJdk);
    } else {
      myCbModuleJdk.setSelectedJdk(null);
    }
    myFreeze = false;
  }

  public void disposeUIResources() {
    myCbModuleJdk = null;
    myJdkPanel = null;
  }
}
