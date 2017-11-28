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
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.tmatesoft.svn.core.SVNURL;

import javax.swing.*;
import javax.swing.event.DocumentEvent;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static org.jetbrains.idea.svn.SvnUtil.append;
import static org.jetbrains.idea.svn.SvnUtil.createUrl;
import static org.jetbrains.idea.svn.dialogs.browser.CopyOptionsDialog.configureRecentMessagesComponent;

public class MkdirOptionsDialog extends DialogWrapper {

  private SVNURL myURL;
  private JTextArea myCommitMessage;
  private JTextField myNameField;
  private JLabel myURLLabel;
  private ComboBox<String> myMessagesBox;
  private JPanel myMainPanel;
  private JLabel myRecentMessagesLabel;
  @NotNull private final SVNURL myOriginalURL;

  public MkdirOptionsDialog(Project project, @NotNull SVNURL url) {
    super(project, true);
    myOriginalURL = url;
    try {
      myURL = append(url, "NewFolder");
    }
    catch (SvnBindException ignore) {
    }
    setTitle("New Remote Folder");
    init();
    myURLLabel.setText(myURL.toDecodedString());
    myNameField.selectAll();
    myNameField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        updateURL();
      }
    });

    if (!project.isDefault()) {
      configureRecentMessagesComponent(project, myMessagesBox, message -> {
        myCommitMessage.setText(message);
        myCommitMessage.selectAll();
      });
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
  }

  @NonNls
  protected String getDimensionServiceKey() {
    return "svn4idea.mkdir.options";
  }

  public String getCommitMessage() {
    return myCommitMessage.getText();
  }

  @Nullable
  public SVNURL getURL() {
    if (getOKAction().isEnabled()) {
      try {
        return createUrl(myURLLabel.getText(), false);
      }
      catch (SvnBindException ignore) {
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
    if (isEmpty(newName)) {
      myURLLabel.setText(myOriginalURL.toDecodedString());
      getOKAction().setEnabled(false);
      return;
    }
    try {
      myURLLabel.setText(append(myOriginalURL, newName).toDecodedString());
      getOKAction().setEnabled(true);
    }
    catch (SvnBindException e) {
      myURLLabel.setText(myOriginalURL.toDecodedString());
      getOKAction().setEnabled(false);
    }
  }
}
