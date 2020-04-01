// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs.browser;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static org.jetbrains.idea.svn.SvnBundle.message;
import static org.jetbrains.idea.svn.dialogs.browser.CopyOptionsDialog.configureRecentMessagesComponent;

public class DeleteOptionsDialog extends DialogWrapper {

  private JTextArea myCommitMessage;
  private final Project myProject;

  public DeleteOptionsDialog(Project project) {
    super(project, true);
    myProject = project;
    setTitle(message("dialog.title.svn.delete"));
    init();
  }

  @Override
  @NonNls
  protected String getDimensionServiceKey() {
    return "svn4idea.delete.options";
  }

  public String getCommitMessage() {
    return myCommitMessage.getText();
  }

  @Override
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
    gc.weightx = 0;
    gc.weighty = 0;
    gc.weightx = 0;
    gc.gridwidth = 3;
    gc.fill = GridBagConstraints.NONE;
    panel.add(new JBLabel(message("label.commit.message")), gc);
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
    panel.add(new JBLabel(message("label.recent.messages")), gc);
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

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myCommitMessage;
  }

}
