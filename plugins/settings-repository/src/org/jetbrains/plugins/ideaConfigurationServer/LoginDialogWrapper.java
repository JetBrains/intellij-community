package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;

import javax.swing.*;

public class LoginDialogWrapper extends DialogWrapper {
  private final LoginDialogPanel myLoginDialogPanel;

  public LoginDialogWrapper(final String failedMessage) {
    super(true);
    myLoginDialogPanel = new LoginDialogPanel() {
      @Override
      protected void showErrorMessage(final String text, final boolean closeOnTimer) {
        Messages.showErrorDialog(text, "Login to IntelliJ Configuration Server");
      }

      @Override
      protected void rememberStartupSettings(final IdeaConfigurationServerSettings settings, final boolean doLogin) {
      }

      @Override
      protected void closeDialog() {
        close(OK_EXIT_CODE);
      }

      protected void doHelp() {
        doHelpAction();
      }
    };

    myLoginDialogPanel.switchToLoginMode();
    myLoginDialogPanel.reset(failedMessage);
    myLoginDialogPanel.stopCounter();

    setTitle("Login to IntelliJ Configuration Server");
    init();
  }

  @Override
  protected String getHelpId() {
    return "reference.shared.settings.login";
  }

  protected JComponent createCenterPanel() {
    return myLoginDialogPanel.getPanel();
  }

  @Override
  public void doCancelAction() {
    myLoginDialogPanel.doCancel();
  }

  @Override
  protected void doOKAction() {
    myLoginDialogPanel.doOk();
  }
}
