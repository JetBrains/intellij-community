package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.help.HelpManager;

import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;
import javax.swing.*;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.svn.SvnBundle;

import java.awt.*;

public class UserNameCredentialsDialog extends DialogWrapper implements DocumentListener {
  private boolean myAllowSave;
  private String myUserName;

  private String myRealm;
  private JTextField myUserNameText;
  private JCheckBox myAllowSaveCheckBox;

  @NonNls
  private static final String HELP_ID = "vcs.subversion.authentication";

  protected UserNameCredentialsDialog(Project project) {
    super(project, true);
    setResizable(false);
  }

  public void setup(String realm, String userName, boolean allowSave) {
    myRealm = realm;
    myUserName = userName;
    myAllowSave = allowSave;
    getHelpAction().setEnabled(true);
    init();
  }
  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HELP_ID);
  }

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

    label = new JLabel(SvnBundle.message("label.auth.user.name"));
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

    gb.gridy += 1;
    gb.gridx = 0;
    gb.gridwidth = 2;
    gb.weightx = 1;
    gb.fill = GridBagConstraints.HORIZONTAL;
    myAllowSaveCheckBox = new JCheckBox(SvnBundle.message("checkbox.auth.keep.for.current.session"));
    panel.add(myAllowSaveCheckBox, gb);
    gb.gridy += 1;
    panel.add(new JSeparator(), gb);

    myAllowSaveCheckBox.setSelected(false);
    myAllowSaveCheckBox.setEnabled(myAllowSave);

    return panel;
  }

  protected String getDimensionServiceKey() {
    return "svn.userNameDialog";
  }

  public JComponent getPreferredFocusedComponent() {
    return myUserNameText;
  }

  public boolean shouldCloseOnCross() {
    return true;
  }

  public boolean isOKActionEnabled() {
    return myUserNameText != null && myUserNameText.getText().trim().length() != 0;
  }

  public String getUserName() {
    return isOK() && myUserNameText != null ? myUserNameText.getText() : null;
  }

  public boolean isSaveAllowed() {
    return isOK() && myAllowSave && myAllowSaveCheckBox != null && myAllowSaveCheckBox.isSelected();
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
