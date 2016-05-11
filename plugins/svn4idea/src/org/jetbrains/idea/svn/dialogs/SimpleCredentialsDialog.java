/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnBundle;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

/**
 * @author alex
 */
public class SimpleCredentialsDialog extends DialogWrapper implements DocumentListener {
  private boolean myAllowSave;
  private String myUserName;
  private Mode myMode;

  private String myRealm;
  private JTextField myUserNameText;
  private JCheckBox myAllowSaveCheckBox;
  private JPasswordField myPasswordText;

  @NonNls private static final String HELP_ID = "vcs.subversion.authentication";

  public SimpleCredentialsDialog(Project project) {
    super(project, true);
    setResizable(false);
  }

  public void setup(String realm, String userName, boolean allowSave) {
    setup(Mode.DEFAULT, realm, userName, allowSave);
  }

  public void setup(Mode mode, String realm, String userName, boolean allowSave) {
    myMode = mode;
    myRealm = realm;
    myUserName = userName;
    myAllowSave = allowSave;
    getHelpAction().setEnabled(true);
    init();
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
    gb.insets = JBUI.insets(2);
    gb.weightx = 1;
    gb.weighty = 0;
    gb.gridwidth = 2;
    gb.gridheight = 1;
    gb.gridx = 0;
    gb.gridy = 0;
    gb.anchor = GridBagConstraints.WEST;
    gb.fill = GridBagConstraints.HORIZONTAL;

    JLabel label = new JLabel(SvnBundle.message("label.auth.authentication.realm", myRealm));
    panel.add(label, gb);

    // user name
    gb.gridy += 1;
    gb.gridwidth = 1;
    gb.weightx = 0;
    gb.fill = GridBagConstraints.NONE;

    label = new JLabel(SvnBundle.message(myMode.equals(Mode.SSH_PASSPHRASE) ? "label.ssh.key.file" : "label.auth.user.name"));
    panel.add(label, gb);

    // user name field
    gb.gridx = 1;
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
    myUserNameText.setEnabled(myMode.equals(Mode.DEFAULT));

    gb.gridy += 1;
    gb.weightx = 0;
    gb.gridx = 0;
    gb.fill = GridBagConstraints.NONE;
    gb.gridwidth = 1;

    label = new JLabel(SvnBundle.message(myMode.equals(Mode.SSH_PASSPHRASE) ? "label.ssh.passphrase" : "label.auth.password"));
    panel.add(label, gb);

    // passworde field
    gb.gridx = 1;
    gb.weightx = 1;
    gb.fill = GridBagConstraints.HORIZONTAL;

    myPasswordText = new JPasswordField();
    panel.add(myPasswordText, gb);
    label.setLabelFor(myPasswordText);

    gb.gridy += 1;
    gb.gridx = 0;
    gb.gridwidth = 2;
    gb.weightx = 1;
    gb.fill = GridBagConstraints.HORIZONTAL;
    myAllowSaveCheckBox = new JCheckBox(SvnBundle.message("checkbox.auth.keep.for.current.session"));
    panel.add(myAllowSaveCheckBox, gb);
    gb.gridy += 1;
    if (! myAllowSave) {
      final JLabel cannotSaveLabel = new JLabel(SvnBundle.message("svn.cannot.save.credentials.store-auth-creds"));
      cannotSaveLabel.setForeground(UIUtil.getInactiveTextColor());
      panel.add(cannotSaveLabel, gb);
      gb.gridy += 1;
    }
    panel.add(new JSeparator(), gb);

    myAllowSaveCheckBox.setSelected(false);
    myAllowSaveCheckBox.setEnabled(myAllowSave);

    return panel;
  }

  protected String getDimensionServiceKey() {
    return "svn.passwordDialog";
  }

  public JComponent getPreferredFocusedComponent() {
    return myUserNameText.isEnabled() ? myUserNameText : myPasswordText;
  }

  public boolean shouldCloseOnCross() {
    return true;
  }

  public boolean isOKActionEnabled() {
    return myUserNameText != null && myUserNameText.getText().trim().length() > 0
           && myPasswordText != null && myPasswordText.getPassword() != null;
  }

  public String getUserName() {
    return isOK() && myUserNameText != null ? myUserNameText.getText() : null;
  }

  public String getPassword() {
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

  public void setSaveEnabled(boolean enabled) {
    myAllowSaveCheckBox.setEnabled(enabled);
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

  private void updateOKButton() {
    getOKAction().setEnabled(isOKActionEnabled());
  }

  public enum Mode {
    SSH_PASSPHRASE,
    SSH_PASSWORD,
    DEFAULT
  }
}
