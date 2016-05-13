package com.jetbrains.edu.learning.stepic;

import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class LoginDialog extends DialogWrapper {
  private final LoginPanel myLoginPanel;

  public LoginDialog() {
    super(false);
    myLoginPanel = new LoginPanel(this);
    setTitle("Login to Stepic");
    setOKButtonText("Login");
    setTitle("Login to Stepic");
    init();
  }

  @NotNull
  protected Action[] createActions() {
    return new Action[]{getOKAction(), new RegisterAction(), getCancelAction()};
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
    final StepicUser user = EduStepicConnector.login(authData.email, authData.password);
    if (user != null) {
      super.doOKAction();
    }
    else {
      setErrorText("Login failed");
    }
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

  protected class RegisterAction extends DialogWrapperAction {
    private RegisterAction() {
      super("Register");
    }

    @Override
    protected void doAction(ActionEvent e) {
      EduStepicConnector.createUser(myLoginPanel.getAuthData().email, myLoginPanel.getAuthData().password);
      doOKAction();
    }
  }

}