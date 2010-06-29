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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;

public class DeleteOptionsDialog extends DialogWrapper {

  private JTextArea myCommitMessage;
  private final Project myProject;

  public DeleteOptionsDialog(Project project) {
    super(project, true);
    myProject = project;
    setTitle("SVN Delete");
    init();
  }

  @NonNls
  protected String getDimensionServiceKey() {
    return "svn4idea.delete.options";
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
    gc.weightx = 0;
    gc.weighty = 0;
    gc.weightx = 0;
    gc.gridwidth = 3;
    gc.fill = GridBagConstraints.NONE;
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

}
