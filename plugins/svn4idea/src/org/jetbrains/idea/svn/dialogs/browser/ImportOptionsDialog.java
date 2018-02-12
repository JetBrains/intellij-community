// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs.browser;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.DepthCombo;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.Url;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import static org.jetbrains.idea.svn.dialogs.browser.CopyOptionsDialog.configureRecentMessagesComponent;

public class ImportOptionsDialog extends DialogWrapper implements ActionListener {

  private final Url myURL;
  private final File myFile;
  private TextFieldWithBrowseButton myPathField;
  private DepthCombo myDepth;
  private JCheckBox myIncludeIgnoredCheckbox;
  private JTextArea myCommitMessage;
  private final Project myProject;

  public ImportOptionsDialog(Project project, Url url, File target) {
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

  public Depth getDepth() {
    return myDepth.getDepth();
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
    gc.insets = JBUI.insets(2);
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
    JLabel urlLabel = new JLabel(myURL.toDecodedString());
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
    myDepth = new DepthCombo(false);
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
    panel.add(ScrollPaneFactory.createScrollPane(myCommitMessage), gc);

    gc.gridy += 1;
    gc.gridwidth = 3;
    gc.gridx = 0;
    gc.weightx = 0;
    gc.weighty = 0;
    gc.anchor = GridBagConstraints.NORTH;
    gc.fill = GridBagConstraints.HORIZONTAL;
    panel.add(new JLabel("Recent Messages: "), gc);
    gc.gridy += 1;


    ComboBox<String> messagesBox = configureRecentMessagesComponent(myProject, new ComboBox<>(), message -> {
      myCommitMessage.setText(message);
      myCommitMessage.selectAll();
    });
    panel.add(messagesBox, gc);

    String lastMessage = VcsConfiguration.getInstance(myProject).getLastNonEmptyCommitMessage();
    if (lastMessage != null) {
      myCommitMessage.setText(lastMessage);
      myCommitMessage.selectAll();
    }
    return panel;
  }


  public JComponent getPreferredFocusedComponent() {
    return myCommitMessage;
  }

  public void actionPerformed(ActionEvent e) {
    // choose directory here/
    FileChooserDescriptor fcd = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    fcd.setShowFileSystemRoots(true);
    fcd.setTitle("Checkout Directory");
    fcd.setDescription("Select directory to checkout from subversion");
    fcd.setHideIgnored(false);
    VirtualFile file = FileChooser.chooseFile(fcd, getContentPane(), myProject, null);
    if (file == null) {
      return;
    }
    myPathField.setText(file.getPath().replace('/', File.separatorChar));
  }
}
