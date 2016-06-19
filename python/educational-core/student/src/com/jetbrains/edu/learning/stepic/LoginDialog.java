package com.jetbrains.edu.learning.stepic;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.edu.learning.ui.LoginPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class LoginDialog extends DialogWrapper {
  protected final LoginPanel myLoginPanel;

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
    return myLoginPanel.getContentPanel();
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
    if (!validateLoginAndPasswordFields()) return;
    final StepicUser user = EduStepicConnector.login(myLoginPanel.getLogin(), myLoginPanel.getPassword());
    if (user != null) {
      doJustOkAction();
    }
    else {
      setErrorText("Login failed");
    }
  }

  public boolean validateLoginAndPasswordFields() {
    if (StringUtil.isEmptyOrSpaces(myLoginPanel.getLogin())) {
      setErrorText("Please, enter your login");
      return false;
    }
    if (StringUtil.isEmptyOrSpaces(myLoginPanel.getPassword())) {
      setErrorText("Please, enter your password");
      return false;
    }
    return true;
  }

  protected void doJustOkAction() {
    super.doOKAction();
  }

  public void clearErrors() {
    setErrorText(null);
  }
}