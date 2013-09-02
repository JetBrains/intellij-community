package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.ide.BrowserUtil;

import javax.swing.*;

public class LoginDialog extends DialogBase {
  private LoginDialogPanel myLoginDialogPanel;

  public LoginDialog(final String failedMessage) {
    super("Login to IntelliJ Configuration Server");
    initDialog(failedMessage);
  }

  private void initDialog(String failedMessage) {
    myLoginDialogPanel = new LoginDialogPanel() {
      @Override
      protected void showErrorMessage(final String text, final boolean closeOnTimer) {
        ErrorMessageDialog.show("Login to IntelliJ Configuration Server", text, closeOnTimer);
      }

      @Override
      protected void rememberStartupSettings(final IdeaServerSettings settings, final boolean doLogin) {
        settings.REMEMBER_SETTINGS = myLoginSilently.isSelected() || myDoNotLogin.isSelected();
        settings.DO_LOGIN = myLoginSilently.isSelected() || myShowDialog.isSelected();
      }

      @Override
      protected void closeDialog() {
        dispose();
      }

      protected void doHelp() {
        BrowserUtil.launchBrowser("http://www.jetbrains.com/idea/serverhelp/");
      }
    };

    myLoginDialogPanel.switchToStartUpMode();
    myLoginDialogPanel.reset(failedMessage);

    init();
  }

  protected JButton getDefaultButton() {
    return myLoginDialogPanel.getDefaultButton();
  }

  protected JPanel getCenterPanel() {
    return myLoginDialogPanel.getPanel();
  }
}
