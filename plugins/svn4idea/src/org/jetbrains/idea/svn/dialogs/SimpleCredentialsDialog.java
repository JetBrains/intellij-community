package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;

import javax.swing.*;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;
import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 25.06.2005
 * Time: 16:47:22
 * To change this template use File | Settings | File Templates.
 */
public class SimpleCredentialsDialog extends DialogWrapper implements DocumentListener {
  private boolean myAllowSave;
  private String myUserName;

  private String myRealm;
  private JTextField myUserNameText;
  private JCheckBox myAllowSaveCheckBox;
  private JPasswordField myPasswordText;

  protected SimpleCredentialsDialog(Project project) {
    super(project, true);
    setResizable(false);
  }

  public void setup(String realm, String userName, boolean allowSave) {
    myRealm = realm;
    myUserName = userName;
    myAllowSave = allowSave;
    init();
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new GridBagLayout());

    GridBagConstraints gb = new GridBagConstraints();

    // top label.
    gb.insets = new Insets(2, 2, 2, 2);
    gb.weightx = 1;
    gb.weighty = 0;
    gb.gridwidth = 2;
    gb.gridheight = 1;
    gb.gridx = 0;
    gb.gridy = 0;
    gb.anchor = GridBagConstraints.WEST;
    gb.fill = GridBagConstraints.HORIZONTAL;

    JLabel label = new JLabel("Authentication realm: '" + myRealm + "'");
    panel.add(label, gb);

    // user name
    gb.gridy += 1;
    gb.gridwidth = 1;
    gb.weightx = 0;
    gb.fill = GridBagConstraints.NONE;

    label = new JLabel("&User name:");
    panel.add(label, gb);

    // user name field
    gb.gridx = 1;
    gb.weightx = 1;
    gb.fill = GridBagConstraints.HORIZONTAL;

    myUserNameText = new JTextField();
    panel.add(myUserNameText, gb);
    DialogUtil.registerMnemonic(label, myUserNameText);

    if (myUserName != null) {
      myUserNameText.setText(myUserName);
    }
    myUserNameText.selectAll();
    myUserNameText.getDocument().addDocumentListener(this);

    gb.gridy += 1;
    gb.weightx = 0;
    gb.gridx = 0;
    gb.fill = GridBagConstraints.NONE;
    gb.gridwidth = 1;

    label = new JLabel("&Password:");
    panel.add(label, gb);

    // passworde field
    gb.gridx = 1;
    gb.weightx = 1;
    gb.fill = GridBagConstraints.HORIZONTAL;

    myPasswordText = new JPasswordField();
    panel.add(myPasswordText, gb);
    DialogUtil.registerMnemonic(label, myPasswordText);

    gb.gridy += 1;
    gb.gridx = 0;
    gb.gridwidth = 2;
    gb.weightx = 1;
    gb.fill = GridBagConstraints.HORIZONTAL;
    myAllowSaveCheckBox = new JCheckBox("Keep only for current &IDEA session");
    DialogUtil.registerMnemonic(myAllowSaveCheckBox);
    panel.add(myAllowSaveCheckBox, gb);
    gb.gridy += 1;
    panel.add(new JSeparator(), gb);

    myAllowSaveCheckBox.setSelected(!myAllowSave);
    myAllowSaveCheckBox.setEnabled(myAllowSave);

    return panel;
  }

  protected String getDimensionServiceKey() {
    return "svn.passwordDialog";
  }

  public JComponent getPreferredFocusedComponent() {
    return myUserNameText;
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
    return isOK() && myAllowSave && myAllowSaveCheckBox != null && !myAllowSaveCheckBox.isSelected();
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
}
