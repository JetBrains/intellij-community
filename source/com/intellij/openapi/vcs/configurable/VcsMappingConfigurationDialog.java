/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.vcs.configurable;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.options.ConfigurationException;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.*;

/**
 * @author yole
 */
public class VcsMappingConfigurationDialog extends DialogWrapper {
  private Project myProject;
  private JComboBox myVCSComboBox;
  private TextFieldWithBrowseButton myDirectoryTextField;
  private JPanel myPanel;
  private JPanel myVcsConfigurablePlaceholder;
  private UnnamedConfigurable myVcsConfigurable;
  private VcsDirectoryMapping myMappingCopy;
  private JComponent myVcsConfigurableComponent;

  public VcsMappingConfigurationDialog(final Project project, final String title) {
    super(project, false);
    myProject = project;
    myVCSComboBox.setModel(VcsDirectoryConfigurationPanel.buildVcsWrappersModel(project));
    myDirectoryTextField.addActionListener(new MyBrowseFolderListener("Select Directory", "Select directory to map to a VCS",
                                                                      myDirectoryTextField, project,
                                                                      new FileChooserDescriptor(false, true, false, false, false, false)));
    myMappingCopy = new VcsDirectoryMapping("", "");
    setTitle(title);
    init();
    myVCSComboBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateVcsConfigurable();
      }
    });
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  public void setMapping(VcsDirectoryMapping mapping) {
    myMappingCopy = new VcsDirectoryMapping(mapping.getDirectory(), mapping.getVcs(), mapping.getRootSettings());
    myVCSComboBox.setSelectedItem(VcsWrapper.fromName(myProject, mapping.getVcs()));
    myDirectoryTextField.setText(FileUtil.toSystemDependentName(mapping.getDirectory()));
    updateVcsConfigurable();
  }

  public void saveToMapping(VcsDirectoryMapping mapping) {
    VcsWrapper wrapper = (VcsWrapper) myVCSComboBox.getSelectedItem();
    mapping.setVcs(wrapper.getOriginal() == null ? "" : wrapper.getOriginal().getName());
    mapping.setDirectory(FileUtil.toSystemIndependentName(myDirectoryTextField.getText()));
    mapping.setRootSettings(myMappingCopy.getRootSettings());
  }

  private void updateVcsConfigurable() {
    if (myVcsConfigurable != null) {
      myVcsConfigurablePlaceholder.remove(myVcsConfigurableComponent);
      myVcsConfigurable.disposeUIResources();
      myVcsConfigurable = null;
    }
    VcsWrapper wrapper = (VcsWrapper) myVCSComboBox.getSelectedItem();
    if (wrapper.getOriginal() != null) {
      UnnamedConfigurable configurable = wrapper.getOriginal().getRootConfigurable(myMappingCopy);
      if (configurable != null) {
        myVcsConfigurable = configurable;
        myVcsConfigurableComponent = myVcsConfigurable.createComponent();
        myVcsConfigurablePlaceholder.add(myVcsConfigurableComponent, BorderLayout.CENTER);
      }
    }
    pack();
  }

  @Override
  protected Action[] createLeftSideActions() {
    return new Action[] { new ConfigureVcsAction() };
  }

  protected void doOKAction() {
    if (myVcsConfigurable != null) {
      try {
        myVcsConfigurable.apply();
      }
      catch(ConfigurationException ex) {
        Messages.showErrorDialog(myPanel, "Invalid VCS options: " + ex.getMessage());
      }
    }
    super.doOKAction();
  }

  private class MyBrowseFolderListener extends ComponentWithBrowseButton.BrowseFolderActionListener<JTextField> {

    public MyBrowseFolderListener(String title, String description, TextFieldWithBrowseButton textField, Project project,
                                  FileChooserDescriptor fileChooserDescriptor) {
      super(title, description, textField, project, fileChooserDescriptor, TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);
    }

    @Override
    protected void onFileChoosen(final VirtualFile chosenFile) {
      String oldText = myDirectoryTextField.getText();
      super.onFileChoosen(chosenFile);
      final VcsWrapper wrapper = (VcsWrapper)myVCSComboBox.getSelectedItem();
      if (oldText.length() == 0 && wrapper.getOriginal() == null) {
        for(AbstractVcs vcs: ProjectLevelVcsManager.getInstance(myProject).getAllVcss()) {
          if (vcs.isVersionedDirectory(chosenFile)) {
            myVCSComboBox.setSelectedItem(new VcsWrapper(vcs));
            break;
          }
        }
      }
    }
  }

  private class ConfigureVcsAction extends AbstractAction {
    public ConfigureVcsAction() {
      super(VcsBundle.message("button.configure"));
    }

    public void actionPerformed(ActionEvent e) {
      VcsWrapper wrapper = (VcsWrapper) myVCSComboBox.getSelectedItem();
      new VcsConfigurationsDialog(myProject, null, wrapper.getOriginal()).show();
    }
  }
}