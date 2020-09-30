// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import static com.intellij.util.ObjectUtils.notNull;
import static org.jetbrains.idea.svn.SvnBundle.message;
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

    String newFolderName = message("value.new.folder.name");
    try {
      myURL = append(url, newFolderName);
    }
    catch (SvnBindException ignore) {
    }
    setTitle(message("dialog.title.new.remote.folder"));
    init();
    myURLLabel.setText(myURL.toDecodedString());

    myNameField.setText(newFolderName);
    myNameField.selectAll();
    myNameField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull final DocumentEvent e) {
        Url newUrl = getNewFolderUrl();

        myURLLabel.setText(notNull(newUrl, myOriginalURL).toDecodedString());
        getOKAction().setEnabled(newUrl != null);
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

  @Override
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

  @Override
  @Nullable
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  private @Nullable Url getNewFolderUrl() {
    String newName = myNameField.getText();
    if (isEmpty(newName)) return null;

    try {
      return append(myOriginalURL, newName);
    }
    catch (SvnBindException ignored) {
      return null;
    }
  }
}
