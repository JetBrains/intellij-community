/**
 * @copyright
 * ====================================================================
 * Copyright (c) 2003-2004 QintSoft.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://subversion.tigris.org/license-1.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 *
 * This software consists of voluntary contributions made by many
 * individuals.  For exact contribution history, see the revision
 * history and logs, available at http://svnup.tigris.org/.
 * ====================================================================
 * @endcopyright
 */
/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NonNls;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

public class SvnConfigurable implements Configurable, ActionListener {

  private Project myProject;
  private JCheckBox myUseDefaultCheckBox;
  private TextFieldWithBrowseButton myConfigurationDirectoryText;
  private JButton myClearAuthButton;
  private JPanel myComponent;

  private JLabel myConfigurationDirectoryLabel;
  private FileChooserDescriptor myBrowserDescriptor;

  @NonNls private static final String HELP_ID = "project.propSubversion";

  public SvnConfigurable(Project project) {
    myProject = project;
  }

  public JComponent createComponent() {
    // checkbox 'use default subversion configuration directory'
    // path to configuration directory (set to default and disable when checkbox is checked).
    myComponent = new JPanel();
    GridBagLayout layout = new GridBagLayout();
    myComponent.setLayout(layout);

    GridBagConstraints gb = new GridBagConstraints();
    gb.weightx = 0;
    gb.weighty = 0;
    gb.gridx = 0;
    gb.gridy = 0;
    gb.anchor = GridBagConstraints.WEST;
    gb.gridwidth = 3;
    myUseDefaultCheckBox = new JCheckBox(SvnBundle.message("checkbox.configure.use.system.default.configuration.directory"));
    add(myUseDefaultCheckBox, gb);
    myUseDefaultCheckBox.addActionListener(this);


    gb.gridy += 1;
    gb.fill = GridBagConstraints.HORIZONTAL;
    gb.gridwidth = 3;
    gb.insets = new Insets(5, 5, 1, 5);
    JLabel label = new JLabel(SvnBundle.message("label.configuration.configuration.directory"));
    myConfigurationDirectoryLabel = label;

    myConfigurationDirectoryText = new TextFieldWithBrowseButton(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (myBrowserDescriptor == null) {
          myBrowserDescriptor = new FileChooserDescriptor(false, true, false, false, false, false);
          myBrowserDescriptor.setShowFileSystemRoots(true);
          myBrowserDescriptor.setTitle(SvnBundle.message("dialog.title.select.configuration.directory"));
          myBrowserDescriptor.setDescription(SvnBundle.message("dialog.description.select.configuration.directory"));
          myBrowserDescriptor.setHideIgnored(false);
        }
        @NonNls String path = myConfigurationDirectoryText.getText().trim();
        path = "file://" + path.replace(File.separatorChar, '/');
        VirtualFile root = VirtualFileManager.getInstance().findFileByUrl(path);

        String oldValue = PropertiesComponent.getInstance().getValue("FileChooser.showHiddens");
        PropertiesComponent.getInstance().setValue("FileChooser.showHiddens", Boolean.TRUE.toString());
        VirtualFile[] files = FileChooser.chooseFiles(myComponent, myBrowserDescriptor, root);
        PropertiesComponent.getInstance().setValue("FileChooser.showHiddens", oldValue);
        if (files == null || files.length != 1 || files[0] == null) {
          return;
        }
        myConfigurationDirectoryText.setText(files[0].getPath().replace('/', File.separatorChar));
      }
    });
    myConfigurationDirectoryText.setEditable(false);
    label.setLabelFor(myConfigurationDirectoryText);
    add(label, gb);

    gb.gridy += 1;
    gb.fill = GridBagConstraints.HORIZONTAL;
    gb.weightx = 1;
    gb.insets = new Insets(0, 5, 5, 0);
    gb.gridwidth = 3;
    add(myConfigurationDirectoryText, gb);

    gb.weighty = 0;
    gb.insets = new Insets(5, 5, 5, 5);
    gb.weightx = 0;
    gb.fill = GridBagConstraints.NONE;
    gb.gridwidth = 1;
    gb.gridheight = 1;
    gb.gridx = 0;
    gb.gridy += 1;
    myClearAuthButton = new JButton(SvnBundle.message("button.text.clear.authentication.cache"));
    myClearAuthButton.addActionListener(this);
    add(myClearAuthButton, gb);
    gb.gridwidth = 2;
    gb.gridx = 1;
    gb.fill = GridBagConstraints.HORIZONTAL;
    label = new JLabel(SvnBundle.message("label.text.delete.stored.credentials"));
    label.setEnabled(false);
    add(label, gb);

    gb.fill = GridBagConstraints.NONE;
    gb.gridheight = 1;
    gb.gridx = 0;
    gb.gridy += 1;
    gb.weighty = 1;
    gb.weightx = 1;
    gb.fill = GridBagConstraints.HORIZONTAL;
    gb.gridwidth = 3;
    add(new JLabel(), gb);
    return myComponent;
  }

  private void add(JComponent component, GridBagConstraints gb) {
    myComponent.add(component, gb);
  }

  public String getDisplayName() {
    return null;
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return HELP_ID;
  }

  public boolean isModified() {
    if (myComponent == null) {
      return false;
    }
    SvnConfiguration configuration = SvnConfiguration.getInstance(myProject);
    if (configuration.isUseDefaultConfiguation() != myUseDefaultCheckBox.isSelected()) {
      return true;
    }
    return !configuration.getConfigurationDirectory().equals(myConfigurationDirectoryText.getText().trim());
  }

  public void apply() throws ConfigurationException {
    SvnConfiguration configuration = SvnConfiguration.getInstance(myProject);
    configuration.setConfigurationDirectory(myConfigurationDirectoryText.getText());
    configuration.setUseDefaultConfiguation(myUseDefaultCheckBox.isSelected());
  }

  public void reset() {
    SvnConfiguration configuration = SvnConfiguration.getInstance(myProject);
    String path = configuration.getConfigurationDirectory();
    if (configuration.isUseDefaultConfiguation() || path == null) {
      path = SVNWCUtil.getDefaultConfigurationDirectory().getAbsolutePath();
    }
    myConfigurationDirectoryText.setText(path);
    myUseDefaultCheckBox.setSelected(configuration.isUseDefaultConfiguation());

    boolean enabled = !myUseDefaultCheckBox.isSelected();
    myConfigurationDirectoryText.setEnabled(enabled);
    myConfigurationDirectoryLabel.setEnabled(enabled);
  }

  public void disposeUIResources() {
    myComponent = null;
  }

  public void actionPerformed(ActionEvent e) {
    if (e.getSource() == myUseDefaultCheckBox) {
      boolean enabled = !myUseDefaultCheckBox.isSelected();
      myConfigurationDirectoryText.setEnabled(enabled);
      myConfigurationDirectoryLabel.setEnabled(enabled);
      SvnConfiguration configuration = SvnConfiguration.getInstance(myProject);
      String path = configuration.getConfigurationDirectory();
      if (!enabled || path == null) {
        myConfigurationDirectoryText.setText(SVNWCUtil.getDefaultConfigurationDirectory().getAbsolutePath());
      }
      else {
        myConfigurationDirectoryText.setText(path);
      }
    }
    else if (e.getSource() == myClearAuthButton) {
      String path = myConfigurationDirectoryText.getText();
      if (path != null) {
        int result = Messages.showYesNoDialog(myComponent, SvnBundle.message("confirmation.text.delete.stored.authentication.information"),
                                              SvnBundle.message("confirmation.title.clear.authentication.cache"),
                                                           Messages.getWarningIcon());
        SvnConfiguration.RUNTIME_AUTH_CACHE.clear();
        SvnApplicationSettings.getInstance().clearAuthenticationInfo();
      }
    }
  }

}

