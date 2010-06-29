/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.DepthCombo;
import org.jetbrains.idea.svn.SvnBundle;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNURL;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

public class ImportOptionsDialog extends DialogWrapper implements ActionListener {

  private final SVNURL myURL;
  private final File myFile;
  private TextFieldWithBrowseButton myPathField;
  private DepthCombo myDepth;
  private JCheckBox myIncludeIgnoredCheckbox;
  private JTextArea myCommitMessage;
  private final Project myProject;

  public ImportOptionsDialog(Project project, SVNURL url, File target) {
    super(project, true);
    myURL = url;
    myFile = target;
    myProject = project;
    setTitle("SVN Import Options");
    init();
  }

  @NonNls
  protected String getDimensionServiceKey() {
    return "svn4idea.import.options";
  }

  public File getTarget() {
    return new File(myPathField.getText());
  }

  public SVNDepth getDepth() {
    return myDepth.getSelectedItem();
  }

  public boolean isIncludeIgnored() {
    return myIncludeIgnoredCheckbox.isSelected();
  }

  public String getCommitMessage() {
    return myCommitMessage.getText();
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

    panel.add(new JLabel("Import to:"), gc);
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
    panel.add(new JLabel("Import from:"), gc);
    gc.gridx += 1;
    gc.gridwidth = 2;
    gc.weightx = 1;
    gc.fill = GridBagConstraints.HORIZONTAL;

    myPathField = new TextFieldWithBrowseButton(this);
    myPathField.setText(myFile.getAbsolutePath());
    myPathField.setEditable(false);
    panel.add(myPathField, gc);

    // other options.
    gc.gridy += 1;
    gc.gridx = 0;
    gc.weightx = 0;
    gc.gridwidth = 3;
    gc.fill = GridBagConstraints.NONE;

    final JLabel depthLabel = new JLabel(SvnBundle.message("label.depth.text"));
    depthLabel.setToolTipText(SvnBundle.message("label.depth.description"));
    panel.add(depthLabel, gc);
    ++gc.gridx;
    myDepth = new DepthCombo();
    panel.add(myDepth, gc);
    depthLabel.setLabelFor(myDepth);

    gc.gridx = 0;
    gc.gridy += 1;
    myIncludeIgnoredCheckbox = new JCheckBox("Include ignored resources");
    myIncludeIgnoredCheckbox.setSelected(true);
    panel.add(myIncludeIgnoredCheckbox, gc);
    gc.gridy += 1;
    panel.add(new JLabel("Commit Message:"), gc);
    gc.gridy += 1;
    gc.gridwidth = 3;
    gc.gridx = 0;
    gc.weightx = 1;
    gc.weighty = 1;
    gc.anchor = GridBagConstraints.NORTH;
    gc.fill = GridBagConstraints.BOTH;

    myCommitMessage = new JTextArea(10, 0);
    myCommitMessage.setWrapStyleWord(true);
    myCommitMessage.setLineWrap(true);
    panel.add(new JBScrollPane(myCommitMessage), gc);

    gc.gridy += 1;
    gc.gridwidth = 3;
    gc.gridx = 0;
    gc.weightx = 0;
    gc.weighty = 0;
    gc.anchor = GridBagConstraints.NORTH;
    gc.fill = GridBagConstraints.HORIZONTAL;
    panel.add(new JLabel("Recent Messages: "), gc);
    gc.gridy += 1;

    final ArrayList<String> messages = VcsConfiguration.getInstance(myProject).getRecentMessages();
    Collections.reverse(messages);

    final String[] model = ArrayUtil.toStringArray(messages);
    final JComboBox messagesBox = new JComboBox(model);
    messagesBox.setRenderer(new MessageBoxCellRenderer());
    panel.add(messagesBox, gc);

    String lastMessage = VcsConfiguration.getInstance(myProject).getLastNonEmptyCommitMessage();
    if (lastMessage != null) {
      myCommitMessage.setText(lastMessage);
      myCommitMessage.selectAll();
    }
    messagesBox.addActionListener(new ActionListener() {

      public void actionPerformed(ActionEvent e) {
        myCommitMessage.setText(messagesBox.getSelectedItem().toString());
        myCommitMessage.selectAll();
      }
    });
    return panel;
  }


  public JComponent getPreferredFocusedComponent() {
    return myCommitMessage;
  }

  public void actionPerformed(ActionEvent e) {
    // choose directory here/
    FileChooserDescriptor fcd = new FileChooserDescriptor(false, true, false, false, false, false);
    fcd.setShowFileSystemRoots(true);
    fcd.setTitle("Checkout Directory");
    fcd.setDescription("Select directory to checkout from subversion");
    fcd.setHideIgnored(false);
    VirtualFile[] files = FileChooser.chooseFiles(getContentPane(), fcd, null);
    if (files.length != 1 || files[0] == null) {
      return;
    }
    myPathField.setText(files[0].getPath().replace('/', File.separatorChar));
  }
}
