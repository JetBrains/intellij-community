/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.javaee;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.xml.XmlBundle;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

public class EditLocationDialog extends DialogWrapper {

  private JTextField myTfUrl;
  private JTextField myTfPath;
  private FixedSizeButton myBtnBrowseLocalPath;
  private final Project myProject;
  private final boolean myShowPath;

  private final String myTitle;
  private final String myName;
  private final String myLocation;
  private boolean myTfShared = true;

  public EditLocationDialog(Project project, boolean showPath) {
    super(project, true);
    myProject = project;
    myShowPath = showPath;
    myTitle = XmlBundle.message("dialog.title.external.resource");
    myName = XmlBundle.message("label.edit.external.resource.uri");
    myLocation = XmlBundle.message("label.edit.external.resource.path");
    init();
  }

  public EditLocationDialog(Project project, boolean showPath, String title, String name, String location) {
    super(project, true);
    myProject = project;
    myShowPath = showPath;
    myTitle = title;
    myName = name;
    myLocation = location;
    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new GridBagLayout());

    panel.add(
      new JLabel(myName),
      new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 5, 3, 5), 0, 0)
    );
    panel.add(
      myTfUrl,
      new GridBagConstraints(0, 1, 2, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(0, 5, 5, 5), 0, 0)
    );

    myTfUrl.setPreferredSize(new Dimension(350, myTfUrl.getPreferredSize().height));

    if (myShowPath) {
      panel.add(
        new JLabel(myLocation),
        new GridBagConstraints(0, 2, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 5, 3, 5), 0, 0)
      );
      panel.add(
        myTfPath,
        new GridBagConstraints(0, 3, 1, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(0, 5, 10, 0), 0, 0)
      );
      panel.add(
        myBtnBrowseLocalPath,
        new GridBagConstraints(1, 3, 1, 1, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(0, 0, 10, 5), 0, 0)
      );

      ComponentWithBrowseButton.MyDoClickAction.addTo(myBtnBrowseLocalPath, myTfPath);
      myBtnBrowseLocalPath.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent ignored) {
          FileChooserDescriptor descriptor = getChooserDescriptor();
          FileChooser.chooseFile(descriptor, myProject, null, new Consumer<VirtualFile>() {
            @Override
            public void consume(VirtualFile file) {
              myTfPath.setText(file.getPath().replace('/', File.separatorChar));
            }
          });
        }
      });
    }
    return panel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTfUrl;
  }

  public NameLocationPair getPair() {
    String path = myTfPath.getText().trim();
    String url = myTfUrl.getText().trim();
    return new NameLocationPair(url, path, myTfShared);
  }

  protected FileChooserDescriptor getChooserDescriptor(){
    return new FileChooserDescriptor(true, false, false, false, true, false);
  }

  @Override
  protected void init() {
    setTitle(myTitle);
    myTfUrl = new JTextField();
    myTfPath = new JTextField();
    myBtnBrowseLocalPath = new FixedSizeButton(myTfPath);
    super.init();
  }

  /**
   * Initializes editor with the passed data.
   */
  public void init(NameLocationPair origin) {
    myTfUrl.setText(origin.myName);
    myTfPath.setText(origin.myLocation);
    myTfShared = origin.myShared;
  }
}
