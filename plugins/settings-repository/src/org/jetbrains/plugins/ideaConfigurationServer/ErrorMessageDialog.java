package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.openapi.ui.Messages;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ErrorMessageDialog extends DialogBase {
  private JButton myCloseButton;
  private JLabel myErrorMessageLabel;
  private JPanel myTimerLabelPanel;
  private JPanel myPanel;

  public static void show(String title, String message, boolean closeOnTimer) {
    new ErrorMessageDialog(title, message, closeOnTimer).setVisible(true);
  }

  public ErrorMessageDialog(final String title, String errorMessage, boolean closeOnTimer) throws HeadlessException {
    super(title);
    myErrorMessageLabel.setText(errorMessage);
    myErrorMessageLabel.setIcon(Messages.getErrorIcon());

    if (closeOnTimer) {
      TimerLabel timerLabel = new TimerLabel();
      myTimerLabelPanel.add(timerLabel.getTimerLabel(), BorderLayout.CENTER);

      timerLabel.startCounter(CANCEL_INTERVAL, new Runnable() {
        @Override
        public void run() {
          dispose();
        }
      });
    }

    myCloseButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        dispose();
      }
    });

    init();
  }

  @Override
  protected JButton getDefaultButton() {
    return myCloseButton;
  }

  @Override
  protected JPanel getCenterPanel() {
    return myPanel;
  }
}
