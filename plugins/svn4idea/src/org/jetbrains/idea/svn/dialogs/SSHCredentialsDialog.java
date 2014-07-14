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
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.util.Consumer;
import com.intellij.util.SystemProperties;
import com.intellij.util.ui.UIUtil;
import com.trilead.ssh2.crypto.PEMDecoder;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnBundle;
import org.tmatesoft.svn.core.internal.io.svn.SVNSSHPrivateKeyUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author alex
 */
public class SSHCredentialsDialog extends DialogWrapper implements ActionListener, DocumentListener {
  private boolean myAllowSave;
  private boolean myIsAgentAllowed;
  private String myUserName;

  private String myRealm;
  private JTextField myUserNameText;
  private JCheckBox myAllowSaveCheckBox;
  private JPasswordField myPasswordText;
  private JPasswordField myPassphraseText;
  private TextFieldWithBrowseButton myKeyFileText;
  private JBRadioButton mySshAgentButton;
  private JRadioButton myPasswordButton;
  private JRadioButton myKeyButton;
  private JLabel myPasswordLabel;
  private JLabel myKeyFileLabel;
  private JLabel myPortLabel;
  private JTextField myPortField;
  private JLabel myPassphraseLabel;
  private final Project myProject;

  @NonNls private static final String HELP_ID = "vcs.subversion.authentication";
  private boolean myKeyFileEmptyOrCorrect;

  public SSHCredentialsDialog(Project project,
                                 String realm,
                                 String userName,
                                 boolean allowSave,
                                 final int port,
                                 boolean isAgentAllowed) {
    super(project, true);
    myProject = project;
    myRealm = realm;
    myUserName = userName;
    myAllowSave = allowSave;
    myIsAgentAllowed = isAgentAllowed;
    setResizable(true);
    getHelpAction().setEnabled(true);
    init();
    myPortField.setText("" + port);
    myKeyFileEmptyOrCorrect = true;
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HELP_ID);
  }

  @NotNull
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new GridBagLayout());

    GridBagConstraints gb = new GridBagConstraints();

    // top label.
    gb.insets = new Insets(2, 2, 2, 2);
    gb.weightx = 1;
    gb.weighty = 0;
    gb.gridwidth = 3;
    gb.gridheight = 1;
    gb.gridx = 0;
    gb.gridy = 0;
    gb.anchor = GridBagConstraints.WEST;
    gb.fill = GridBagConstraints.HORIZONTAL;

    JLabel label = new JLabel(SvnBundle.message("label.ssh.authentication.realm", myRealm));
    panel.add(label, gb);

    // user name
    gb.gridy += 1;
    gb.gridwidth = 1;
    gb.weightx = 0;
    gb.fill = GridBagConstraints.NONE;

    label = new JLabel(SvnBundle.message("label.ssh.user.name"));
    panel.add(label, gb);

    // user name field
    gb.gridx = 1;
    gb.gridwidth = 2;
    gb.weightx = 1;
    gb.fill = GridBagConstraints.HORIZONTAL;

    myUserNameText = new JTextField();
    panel.add(myUserNameText, gb);
    label.setLabelFor(myUserNameText);

    if (myUserName != null) {
      myUserNameText.setText(myUserName);
    }
    myUserNameText.selectAll();
    myUserNameText.getDocument().addDocumentListener(this);

    gb.gridy += 1;
    gb.weightx = 0;
    gb.gridx = 0;
    gb.fill = GridBagConstraints.NONE;
    gb.gridwidth = 3;

    // ssh agent type
    mySshAgentButton = new JBRadioButton(SvnBundle.message("radio.ssh.authentication.with.agent"));
    panel.add(mySshAgentButton, gb);
    gb.gridy += 1;
    gb.weightx = 0;
    gb.gridx = 0;
    gb.fill = GridBagConstraints.NONE;
    gb.gridwidth = 3;

    // password type
    myPasswordButton = new JRadioButton(SvnBundle.message("radio.ssh.authentication.with.password"));
    panel.add(myPasswordButton, gb);

    gb.gridy += 1;
    gb.weightx = 0;
    gb.gridx = 0;
    gb.gridwidth = 1;
    gb.fill = GridBagConstraints.NONE;
    gb.gridwidth = 1;

    myPasswordLabel = new JLabel(SvnBundle.message("label.ssh.password"));
    panel.add(myPasswordLabel, gb);

    // passworde field
    gb.gridx = 1;
    gb.weightx = 1;
    gb.gridwidth = 2;
    gb.fill = GridBagConstraints.HORIZONTAL;

    myPasswordText = new JPasswordField();
    panel.add(myPasswordText, gb);
    myPasswordLabel.setLabelFor(myPasswordText);

    gb.gridy += 1;
    gb.weightx = 0;
    gb.gridx = 0;
    gb.fill = GridBagConstraints.NONE;
    gb.gridwidth = 3;
    myKeyButton = new JRadioButton(SvnBundle.message("radio.ssh.authentication.private.key"));
    panel.add(myKeyButton, gb);

    // key file.
    gb.gridy += 1;
    gb.weightx = 0;
    gb.gridx = 0;
    gb.gridwidth = 1;
    gb.fill = GridBagConstraints.NONE;
    gb.gridwidth = 1;

    myKeyFileLabel = new JLabel(SvnBundle.message("label.ssh.key.file"));
    panel.add(myKeyFileLabel, gb);

    // key field
    gb.gridx = 1;
    gb.weightx = 1;
    gb.gridwidth = 2;
    gb.fill = GridBagConstraints.HORIZONTAL;

    myKeyFileText = new TextFieldWithBrowseButton(this);
    myKeyFileText.setEditable(false);
    panel.add(myKeyFileText, gb);
    myKeyFileLabel.setLabelFor(myKeyFileText);

    gb.gridy += 1;
    gb.weightx = 0;
    gb.gridx = 0;
    gb.gridwidth = 1;
    gb.fill = GridBagConstraints.NONE;
    gb.gridwidth = 1;

    myPassphraseLabel = new JLabel(SvnBundle.message("label.ssh.passphrase"));
    panel.add(myPassphraseLabel, gb);

    // key field
    gb.gridx = 1;
    gb.weightx = 1;
    gb.gridwidth = 2;
    gb.fill = GridBagConstraints.HORIZONTAL;

    myPassphraseText = new JPasswordField(30);
    panel.add(myPassphraseText, gb);
    myPassphraseText.getDocument().addDocumentListener(this);
    myPassphraseText.addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent e) {
        checkKeyFile();
        updateOKButton();
      }
    });

    myPassphraseLabel.setLabelFor(myPassphraseText);

    // key file.
    gb.gridy += 1;
    gb.weightx = 0;
    gb.gridx = 0;
    gb.gridwidth = 1;
    gb.fill = GridBagConstraints.NONE;
    gb.gridwidth = 1;

    myPortLabel = new JLabel(SvnBundle.message("label.ssh.port"));
    panel.add(myPortLabel, gb);

    // key field
    gb.gridx = 1;
    gb.weightx = 0;
    gb.gridwidth = 2;
    gb.fill = GridBagConstraints.NONE;

    myPortField = new JTextField();
    myPortField.setColumns(6);
    panel.add(myPortField, gb);
    myPortLabel.setLabelFor(myPortField);
    myPortField.setText("22");
    myPortField.setMinimumSize(myPortField.getPreferredSize());
    myPortField.getDocument().addDocumentListener(this);

    ButtonGroup group = new ButtonGroup();
    group.add(mySshAgentButton);
    group.add(myPasswordButton);
    group.add(myKeyButton);

    mySshAgentButton.setEnabled(myIsAgentAllowed);
    group.setSelected((myIsAgentAllowed ? mySshAgentButton : myPasswordButton).getModel(), true);

    gb.gridy += 1;
    gb.gridx = 0;
    gb.gridwidth = 3;
    gb.weightx = 1;
    gb.anchor = GridBagConstraints.WEST;
    gb.fill = GridBagConstraints.HORIZONTAL;
    myAllowSaveCheckBox = new JCheckBox(SvnBundle.message("checkbox.ssh.keep.for.current.session"));
    panel.add(myAllowSaveCheckBox, gb);
    if (! myAllowSave) {
      gb.gridy += 1;
      final JLabel cannotSaveLabel = new JLabel(SvnBundle.message("svn.cannot.save.credentials.store-auth-creds"));
      cannotSaveLabel.setForeground(UIUtil.getInactiveTextColor());
      panel.add(cannotSaveLabel, gb);
    }
    gb.gridy += 1;
    panel.add(new JSeparator(), gb);

    gb.gridy += 1;
    gb.weighty = 1;
    panel.add(new JLabel(), gb);

    myAllowSaveCheckBox.setSelected(false);
    myAllowSaveCheckBox.setEnabled(myAllowSave);

    mySshAgentButton.addActionListener(this);
    myKeyButton.addActionListener(this);
    myPasswordButton.addActionListener(this);

    updateFields();
    updateOKButton();

    return panel;
  }

  public JComponent getPreferredFocusedComponent() {
    return myUserNameText;
  }

  public boolean shouldCloseOnCross() {
    return true;
  }

  protected String getDimensionServiceKey() {
    return "svn.sshPasswordDialog";
  }

  public boolean isOKActionEnabled() {
    boolean ok = myUserNameText != null && myUserNameText.getText().trim().length() > 0;
    if (ok) {
      if (myPasswordButton.isSelected()) {
        ok = myPasswordText != null && myPasswordText.getPassword() != null;
      }
      else if (myKeyButton.isSelected()) {
        if (! myKeyFileEmptyOrCorrect) return false;
        ok = myKeyFileText != null && myKeyFileText.getText().trim().length() > 0;
      }
      else {
        ok = mySshAgentButton.isSelected();
      }
        if (ok) {
            String portNumber = myPortField.getText();
            try {
                int port = Integer.parseInt(portNumber);
                ok = port > 0;
            } catch (NumberFormatException nfe) {
                ok = false;
            }
        }
    }
    return ok;
  }

  public boolean isSshAgentSelected() {
    return mySshAgentButton.isSelected();
  }

  public String getUserName() {
    return isOK() && myUserNameText != null ? myUserNameText.getText() : null;
  }

  public String getKeyFile() {
    if (myKeyFileText.isEnabled()) {
      return myKeyFileText.getText();
    }
    return null;
  }

  public int getPortNumber() {
      String portNumber = myPortField.getText();
      int port = 22;
      try {
          port = Integer.parseInt(portNumber);
      } catch (NumberFormatException nfe) {
          port = 22;
      }
      if (port <= 0) {
          port = 22;
      }
      return port;
  }

  public String getPassphrase() {
    if (myPassphraseText == null || !myPassphraseText.isEnabled()) {
      return null;
    }
    if (isOK()) {
      char[] pwd = myPassphraseText.getPassword();
      if (pwd != null) {
        return new String(pwd);
      }
    }
    return null;
  }

  public String getPassword() {
    if (myPasswordText != null && !myPasswordText.isEnabled()) {
      return null;
    }
    if (isOK() && myPasswordText != null) {
      char[] pwd = myPasswordText.getPassword();
      if (pwd != null) {
        return new String(pwd);
      }
    }
    return null;
  }

  public boolean isSaveAllowed() {
    return isOK() && myAllowSave && myAllowSaveCheckBox != null && myAllowSaveCheckBox.isSelected();
  }

  public void actionPerformed(ActionEvent e) {
    if (e.getSource() == myPasswordButton || e.getSource() == myKeyButton || e.getSource() == mySshAgentButton) {
      updateFields();
      checkKeyFile();
      updateOKButton();
    }
    else {
      final String[] path = {myKeyFileText.getText()};
      VirtualFile file;
      if (path[0] != null && path[0].trim().length() > 0) {
        path[0] = "file://" + path[0].replace(File.separatorChar, '/');
        file = VirtualFileManager.getInstance().findFileByUrl(path[0]);
      }
      else {
        path[0] = "file://" + SystemProperties.getUserHome() + "/.ssh/identity";
        path[0] = path[0].replace(File.separatorChar, '/');
        file = VirtualFileManager.getInstance().findFileByUrl(path[0]);
        if (file == null || !file.exists()) {
          path[0] = "file://" + SystemProperties.getUserHome() + "/.ssh";
          file = VirtualFileManager.getInstance().findFileByUrl(path[0]);
        }
      }

      FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
        .withTitle(SvnBundle.message("dialog.title.openssh.v2.private.key"))
        .withDescription(SvnBundle.message("dialog.description.openssh.v2.private.key"))
        .withShowFileSystemRoots(true)
        .withHideIgnored(false)
        .withShowHiddenFiles(true);

      FileChooser.chooseFiles(descriptor, myProject, file, new Consumer<List<VirtualFile>>() {
        @Override
        public void consume(List<VirtualFile> files) {
          if (files.size() == 1) {
            path[0] = FileUtil.toSystemDependentName(files.get(0).getPath());
            myKeyFileText.setText(path[0]);
          }
          checkKeyFile();
          updateOKButton();
        }
      });
    }
  }

  private void checkKeyFile() {
    myKeyFileEmptyOrCorrect = true;
    setErrorText(null);

    if (! myKeyButton.isSelected()) return;
    final String text = myKeyFileText.getText();
    if (StringUtil.isEmptyOrSpaces(text)) return;
    final File file = new File(text);
    if (! file.exists()) {
      setErrorText("Private key file does not exist");
      myKeyFileEmptyOrCorrect = false;
      return;
    }
    final char[] password = myPassphraseText.getPassword();
    try {
      PEMDecoder.decode(SVNSSHPrivateKeyUtil.readPrivateKey(file), String.valueOf(password));
    }
    catch (IOException e) {
      setErrorText("Private key file is not valid. " + e.getMessage());
      myKeyFileEmptyOrCorrect = false;
    }
  }

  private void updateOKButton() {
    getOKAction().setEnabled(isOKActionEnabled());
  }

  private void updateFields() {
    myPasswordText.setEnabled(myPasswordButton.isSelected());
    myPasswordLabel.setEnabled(myPasswordButton.isSelected());

    myKeyFileText.setEnabled(myKeyButton.isSelected());
    myKeyFileLabel.setEnabled(myKeyButton.isSelected());
    myPassphraseLabel.setEnabled(myKeyButton.isSelected());
    myPassphraseText.setEnabled(myKeyButton.isSelected());
  }

  public void insertUpdate(DocumentEvent e) {
    updateOKButton();
  }

  public void removeUpdate(DocumentEvent e) {
    updateOKButton();
  }

  public void changedUpdate(DocumentEvent e) {
    updateOKButton();
  }

  @Override
  protected void doOKAction() {
    checkKeyFile();
    updateOKButton();
    if (! isOKActionEnabled()) return;
    super.doOKAction();
  }
}
