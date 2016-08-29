package com.jetbrains.edu.learning.stepik;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.ui.LoginPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class LoginDialog extends DialogWrapper {
  protected final LoginPanel myLoginPanel;

  public LoginDialog() {
    super(false);
    myLoginPanel = new LoginPanel(this);
    setTitle("Login to Stepik");
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
    return "login_to_stepik";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myLoginPanel.getPreferableFocusComponent();
  }

  @Override
  protected void doOKAction() {
    if (!validateLoginAndPasswordFields()) return;
    StepikUser basicUser = new StepikUser(myLoginPanel.getLogin(), myLoginPanel.getPassword());
    final StepikUser user = StepikConnectorLogin.minorLogin(basicUser);
    if (user != null) {
      doJustOkAction();
      final Project project = ProjectUtil.guessCurrentProject(myLoginPanel.getContentPanel());
      StudyTaskManager.getInstance(project).setUser(user);
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