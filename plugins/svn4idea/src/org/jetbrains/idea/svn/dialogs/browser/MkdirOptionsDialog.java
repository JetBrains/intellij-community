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
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;

public class MkdirOptionsDialog extends DialogWrapper {

  private SVNURL myURL;
  private JTextArea myCommitMessage;
  private JTextField myNameField;
  private JLabel myURLLabel;
  private JComboBox myMessagesBox;
  private JPanel myMainPanel;
  private JLabel myRecentMessagesLabel;
  private final SVNURL myOriginalURL;

  public MkdirOptionsDialog(Project project, SVNURL url) {
    super(project, true);
    myOriginalURL = url;
    try {
      myURL = url.appendPath("NewFolder", true);
    }
    catch (SVNException ignore) {
    }
    setTitle("New Remote Folder");
    init();
    myURLLabel.setText(myURL.toString());
    myNameField.selectAll();
    myNameField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        updateURL();
      }
    });

    if (!project.isDefault()) {
      final ArrayList<String> messages = VcsConfiguration.getInstance(project).getRecentMessages();
      Collections.reverse(messages);

      final String[] model = ArrayUtil.toStringArray(messages);
      myMessagesBox.setModel(new DefaultComboBoxModel(model));
      myMessagesBox.setRenderer(new MessageBoxCellRenderer());
    }
    else {
      myRecentMessagesLabel.setVisible(false);
      myMessagesBox.setVisible(false);
    }

    String lastMessage = VcsConfiguration.getInstance(project).getLastNonEmptyCommitMessage();
    if (lastMessage != null) {
      myCommitMessage.setText(lastMessage);
      myCommitMessage.selectAll();
    }
    myMessagesBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myCommitMessage.setText(myMessagesBox.getSelectedItem().toString());
        myCommitMessage.selectAll();
      }
    });
  }

  @NonNls
  protected String getDimensionServiceKey() {
    return "svn4idea.mkdir.options";
  }

  public String getCommitMessage() {
    return myCommitMessage.getText();
  }

  public SVNURL getURL() {
    if (getOKAction().isEnabled()) {
      try {
        return SVNURL.parseURIEncoded(myURLLabel.getText());
      }
      catch (SVNException ignore) {
      }
    }
    return null;
  }

  public String getName() {
    return myNameField.getText();
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  private void updateURL() {
    String newName = myNameField.getText();
    if (newName == null || "".equals(newName)) {
      myURLLabel.setText(myOriginalURL.toString());
      getOKAction().setEnabled(false);
      return;
    }
    try {
      myURLLabel.setText(myOriginalURL.appendPath(newName, false).toString());
      getOKAction().setEnabled(true);
    }
    catch (SVNException e) {
      myURLLabel.setText(myOriginalURL.toString());
      getOKAction().setEnabled(false);
    }
  }
}
