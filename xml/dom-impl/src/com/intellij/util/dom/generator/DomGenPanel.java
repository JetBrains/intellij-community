/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.util.dom.generator;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.FileTypeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public class DomGenPanel {
  private JPanel mainPanel;
  private JTextField myNamespace;
  private JTextField mySuperClass;
  private TextFieldWithBrowseButton mySchemaLocation;
  private JTextField myPackage;
  private TextFieldWithBrowseButton myOutputDir;
  private final Project myProject;

  public DomGenPanel(Project project) {
    myProject = project;
  }

  private void createUIComponents() {
    mySchemaLocation = new TextFieldWithBrowseButton();
    final String title = "Choose XSD or DTD schema";
    mySchemaLocation.addBrowseFolderListener(title, "", myProject, new FileTypeDescriptor(title, "xsd", "dtd"));
    myOutputDir = new TextFieldWithBrowseButton();
    myOutputDir.addBrowseFolderListener("Select Output For Generated Files", "", myProject, FileChooserDescriptorFactory.getDirectoryChooserDescriptor("Output For Generated Files"));
  }

  public JComponent getComponent() {
    return mainPanel;
  }

  public NamespaceDesc getNamespaceDescriptor() {
    return new NamespaceDesc(myNamespace.getText().trim(), myPackage.getText().trim(), mySuperClass.getText().trim(), "", null, null, null, null);
  }

  public String getLocation() {
    return mySchemaLocation.getText();
  }

  public String getOutputDir() {
    return myOutputDir.getText();
  }
}
