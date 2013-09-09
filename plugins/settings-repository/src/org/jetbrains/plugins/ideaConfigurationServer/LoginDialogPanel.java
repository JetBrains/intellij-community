package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.ui.ClickListener;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;

public abstract class LoginDialogPanel {
  private JButton myLoginButton;
  private JButton myCancelButton;
  private JTextField myLogin;
  private JTextField token;
  private JLabel myFailedMessage;
  private JPanel myPanel;
  private JPanel myTimerPanel;
  private JLabel myCreateAccountLabel;
  protected JCheckBox myShowDialog;
  private JPanel myLoginModePanel;
  private JButton myHelpButton;
  private JLabel myPromptLabel;
  private ActionListener myActionListener;

  private final TimerLabel myTimerLabel = new TimerLabel();

  public LoginDialogPanel() {
    myLoginButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        doOk();
      }
    });

    myCancelButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        doCancel();
      }
    });

    myHelpButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        doHelp();
      }
    });

    myPromptLabel.setText(myPromptLabel.getText().replace("IntelliJ IDEA", ApplicationNamesInfo.getInstance().getFullProductName()));

    myTimerPanel.add(myTimerLabel.getTimerLabel(), BorderLayout.CENTER);

    new ClickListener() {
      @Override
      public boolean onClick(MouseEvent e, int clickCount) {
        if (myActionListener != null) {
          myActionListener.actionPerformed(null);
        }
        BrowserUtil.launchBrowser("http://account.jetbrains.com");
        return true;
      }
    }.installOn(myCreateAccountLabel);

    myCreateAccountLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
  }

  protected abstract void doHelp();

  protected void doCancel() {
    closeDialog(false);
  }

  protected void doOk() {
    doLogin(false);
  }

  public void switchToStartUpMode() {
    myLoginButton.setText("Login");
    myCancelButton.setText("Skip");
    myLoginModePanel.setVisible(true);
    myShowDialog.setVisible(true);
    myLoginButton.setVisible(true);
    myCancelButton.setVisible(true);
    myHelpButton.setVisible(true);
  }

  public void switchToLoginMode() {
    myLoginButton.setText("Login");
    myCancelButton.setText("Cancel");
    myLoginModePanel.setVisible(false);
    myShowDialog.setVisible(false);
    myLoginButton.setVisible(false);
    myCancelButton.setVisible(false);
    myHelpButton.setVisible(false);
  }

  public void reset(final String failedMessage) {
    if (failedMessage != null) {
      myFailedMessage.setVisible(true);
      myFailedMessage.setText(failedMessage);
    }
    IcsSettings settings = IcsManager.getInstance().getSettings();
    myLogin.setText(settings.getLogin());
    token.setText(settings.getToken());
    myShowDialog.setSelected(true);
  }

  private void closeDialog(final boolean doLogin) {
    stopCounter();
    rememberStartupSettings(IcsManager.getInstance().getSettings(), doLogin);

    closeDialog();
  }

  public JPanel getPanel() {
    return myPanel;
  }

  public JButton getDefaultButton() {
    return myLoginButton;
  }

  protected abstract void rememberStartupSettings(final IcsSettings settings, final boolean doLogin);

  protected abstract void closeDialog();

  protected abstract void showErrorMessage(final String text, final boolean closeOnTimer);

  public void addActionListener(final ActionListener actionListener) {
    myShowDialog.addActionListener(actionListener);
    myLogin.addActionListener(actionListener);
    token.addActionListener(actionListener);
    DocumentListener docListener = new DocumentListener() {
      @Override
      public void insertUpdate(final DocumentEvent e) {
        actionListener.actionPerformed(null);
      }

      @Override
      public void removeUpdate(final DocumentEvent e) {
        actionListener.actionPerformed(null);
      }

      @Override
      public void changedUpdate(final DocumentEvent e) {
        actionListener.actionPerformed(null);
      }
    };
    myLogin.getDocument().addDocumentListener(docListener);
    token.getDocument().addDocumentListener(docListener);
    myActionListener = actionListener;
  }

  public void stopCounter() {
    myTimerLabel.stopCounter();
  }

  public void startCounter(final int cancelInterval, final Runnable runnable) {
    myTimerLabel.startCounter(cancelInterval, runnable);
  }

  public void tryToLoginSilently() {
    if (myLogin.getText().length() > 0) {
      doLogin(true);
    }
    else {
      closeDialog(true);
    }
  }

  private void doLogin(final boolean onTimer) {
    try {
      IcsManager.getInstance().getSettings().update(myLogin.getText(), token.getText());
      IcsManager.getInstance().connectAndUpdateRepository();
      closeDialog(true);
    }
    catch (Exception e) {
      showErrorMessage(prepareMessage(exceptionMessage(e)), onTimer);
      if (onTimer) {
        closeDialog(true);
      }
      else {
        myTimerLabel.stopCounter();
      }
    }
  }

  private static String exceptionMessage(Exception e) {
    return e.getLocalizedMessage() == null ? e.getMessage() : e.getLocalizedMessage();
  }

  private static String prepareMessage(String text) {
    if (text == null) return "null";
    if (text.startsWith("<!DOCTYPE")) {
      int endOfDoctype = text.indexOf(">");
      return text.substring(endOfDoctype + 1);
    }
    else {
      return "Cannot login: " + text;
    }
  }
}
