package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.ide.BrowserUtil;

import javax.swing.*;

public class LoginDialog extends DialogBase {
  private LoginDialogPanel loginDialogPanel;

  public LoginDialog(String failedMessage) {
    super("Login to IntelliJ Configuration Server");

    initDialog(failedMessage);
  }

  private void initDialog(String failedMessage) {
    loginDialogPanel = new LoginDialogPanel() {
      @Override
      protected void showErrorMessage(String text, boolean closeOnTimer) {
        ErrorMessageDialog.show("Login to IntelliJ Configuration Server", text, closeOnTimer);
      }

      @Override
      protected void rememberStartupSettings(IcsSettings settings, boolean doLogin) {
      }

      @Override
      protected void closeDialog() {
        dispose();
      }

      @Override
      protected void doHelp() {
        BrowserUtil.launchBrowser("http://www.jetbrains.com/idea/serverhelp/");
      }
    };

    loginDialogPanel.switchToStartUpMode();
    loginDialogPanel.reset(failedMessage);

    init();
  }

  @Override
  protected JButton getDefaultButton() {
    return loginDialogPanel.getDefaultButton();
  }

  @Override
  protected JPanel getCenterPanel() {
    return loginDialogPanel.getPanel();
  }
}
