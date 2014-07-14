/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.dialogs.browser;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.DepthCombo;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.api.Depth;
import org.tmatesoft.svn.core.SVNURL;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

public class ExportOptionsDialog extends DialogWrapper implements ActionListener {

  private final SVNURL myURL;
  private final Project myProject;
  private final File myFile;
  private TextFieldWithBrowseButton myPathField;
  private DepthCombo myDepth;
  private JCheckBox myExternalsCheckbox;
  private JCheckBox myForceCheckbox;
  private JComboBox myEOLStyleBox;

  public ExportOptionsDialog(Project project, SVNURL url, File target) {
    super(project, true);
    myURL = url;
    myProject = project;
    myFile = target;
    setTitle("SVN Export Options");
    init();
  }

  @NonNls
  protected String getDimensionServiceKey() {
    return "svn4idea.export.options";
  }

  public File getTarget() {
    return new File(myPathField.getText());
  }

  public Depth getDepth() {
    return myDepth.getDepth();
  }

  public boolean isForce() {
    return myForceCheckbox.isSelected();
  }

  public boolean isIgnoreExternals() {
    return !myExternalsCheckbox.isSelected();
  }

  public String getEOLStyle() {
    if (myEOLStyleBox.getSelectedIndex() == 0) {
      return null;
    }
    return (String) myEOLStyleBox.getSelectedItem();
  }

  @Nullable
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new GridBagLayout());

    GridBagConstraints gc = new GridBagConstraints();
    gc.insets = new Insets(2, 2, 2, 2);
    gc.gridwidth = 1;
    gc.gridheight = 1;
    gc.gridx = 0;
    gc.gridy = 0;
    gc.anchor = GridBagConstraints.WEST;
    gc.fill = GridBagConstraints.NONE;
    gc.weightx = 0;
    gc.weighty = 0;

    panel.add(new JLabel("Export:"), gc);
    gc.gridx += 1;
    gc.gridwidth = 2;
    gc.weightx = 1;
    gc.fill = GridBagConstraints.HORIZONTAL;
    JLabel urlLabel = new JLabel(myURL.toString());
    urlLabel.setFont(urlLabel.getFont().deriveFont(Font.BOLD));
    panel.add(urlLabel, gc);

    gc.gridy += 1;
    gc.gridwidth = 1;
    gc.gridx = 0;
    gc.weightx = 0;
    gc.fill = GridBagConstraints.NONE;
    panel.add(new JLabel("Destination:"), gc);
    gc.gridx += 1;
    gc.gridwidth = 2;
    gc.weightx = 1;
    gc.fill = GridBagConstraints.HORIZONTAL;

    myPathField = new TextFieldWithBrowseButton(this);
    myPathField.setText(myFile.getAbsolutePath());
    myPathField.setEditable(false);
    panel.add(myPathField, gc);

    gc.gridy += 1;
    gc.gridx = 0;
    gc.weightx = 0;
    gc.gridwidth = 3;
    gc.fill = GridBagConstraints.NONE;

    // other options.
    final JLabel depthLabel = new JLabel(SvnBundle.message("label.depth.text"));
    depthLabel.setToolTipText(SvnBundle.message("label.depth.description"));
    panel.add(depthLabel, gc);
    ++ gc.gridx;
    myDepth = new DepthCombo(false);
    panel.add(myDepth, gc);
    depthLabel.setLabelFor(myDepth);

    gc.gridx = 0;
    gc.gridy += 1;
    myForceCheckbox = new JCheckBox("Replace existing files");
    myForceCheckbox.setSelected(true);
    panel.add(myForceCheckbox, gc);
    gc.gridy += 1;
    myExternalsCheckbox = new JCheckBox("Include externals locations");
    myExternalsCheckbox.setSelected(true);
    panel.add(myExternalsCheckbox, gc);
    gc.gridy += 1;
    gc.gridwidth = 2;
    panel.add(new JLabel("Override 'native' EOLs with:"), gc);
    gc.gridx += 2;
    gc.gridwidth = 1;
    myEOLStyleBox = new JComboBox(new Object[] {"None", "LF", "CRLF", "CR"});
    panel.add(myEOLStyleBox, gc);
    gc.gridy += 1;
    gc.gridwidth = 3;
    gc.gridx = 0;
    gc.weightx = 1;
    gc.weighty = 1;
    gc.anchor = GridBagConstraints.SOUTH;
    gc.fill = GridBagConstraints.HORIZONTAL;
    panel.add(new JSeparator(), gc);
    return panel;
  }

  public void actionPerformed(ActionEvent e) {
    // choose directory here/
    FileChooserDescriptor fcd = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    fcd.setShowFileSystemRoots(true);
    fcd.setTitle("Export Directory");
    fcd.setDescription("Select directory to export from subversion");
    fcd.setHideIgnored(false);
    VirtualFile file = FileChooser.chooseFile(fcd, getContentPane(), myProject, null);
    if (file == null) {
      return;
    }
    myPathField.setText(file.getPath().replace('/', File.separatorChar));
  }
}
