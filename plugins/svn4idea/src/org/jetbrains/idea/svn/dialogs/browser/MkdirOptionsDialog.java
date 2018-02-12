// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs.browser;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.commandLine.SvnBindException;

import javax.swing.*;
import javax.swing.event.DocumentEvent;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static org.jetbrains.idea.svn.SvnUtil.append;
import static org.jetbrains.idea.svn.SvnUtil.createUrl;
import static org.jetbrains.idea.svn.dialogs.browser.CopyOptionsDialog.configureRecentMessagesComponent;

public class MkdirOptionsDialog extends DialogWrapper {

  private Url myURL;
  private JTextArea myCommitMessage;
  private JTextField myNameField;
  private JLabel myURLLabel;
  private ComboBox<String> myMessagesBox;
  private JPanel myMainPanel;
  private JLabel myRecentMessagesLabel;
  @NotNull private final Url myOriginalURL;

  public MkdirOptionsDialog(Project project, @NotNull Url url) {
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
  public Url getURL() {
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
