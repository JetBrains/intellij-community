package com.jetbrains.edu.stepic;

import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class LoginDialog extends DialogWrapper {
  private final LoginPanel myLoginPanel;

  public LoginDialog() {
    super(false);
    myLoginPanel = new LoginPanel(this);
    setTitle("Login to Stepic");
    setOKButtonText("Login");
    init();
  }

  @NotNull
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction()};
  }

  @Override
  protected JComponent createCenterPanel() {
    return myLoginPanel.getPanel();
  }

  @Override
  protected String getHelpId() {
    return "login_to_stepic";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myLoginPanel.getPreferableFocusComponent();
  }

  @Override
  protected void doOKAction() {
    AuthDataHolder authData = myLoginPanel.getAuthData();
    final boolean success = EduStepicConnector.login(authData.email, authData.password);
    if (!success) {
      setErrorText("Log in failed");
    }
    super.doOKAction();
  }

  public void clearErrors() {
    setErrorText(null);
  }

  public static class AuthDataHolder {
    String email;
    String password;

    public AuthDataHolder(String login, String password) {
      email = login;
      this.password = password;
    }
  }
}