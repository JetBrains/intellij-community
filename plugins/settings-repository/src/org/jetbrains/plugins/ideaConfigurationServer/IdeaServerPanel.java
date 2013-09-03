package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.AppUIUtil;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class IdeaServerPanel {
  private JLabel myStatusLabel;
  private JPanel myPanel;
  private JRadioButton myLoginSilently;
  private JRadioButton myDoNotLogin;
  private JRadioButton myShowDialog;
  private JButton myActionButton;
  private JPanel myStartupPanel;
  private final DialogWrapper myParent;

  public IdeaServerPanel(DialogWrapper parent) {
    myParent = parent;
    final IcsSettings settings = IcsManager.getInstance().getIdeaServerSettings();

    final TitledBorder border = (TitledBorder)myStartupPanel.getBorder();
    border.setTitle("On next " + ApplicationNamesInfo.getInstance().getProductName() + " startup");

    myActionButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        try {
          //if (IcsManager.getInstance().getStatus() != IdeaConfigurationServerStatus.LOGGED_IN) {
          //  requestCredentials(null, false);
          //}
        }
        finally {
          update();
        }
      }
    });

    update();

    ActionListener actionListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        IcsSettings settings = IcsManager.getInstance().getIdeaServerSettings();
      }
    };
    myLoginSilently.addActionListener(actionListener);
    myShowDialog.addActionListener(actionListener);
    myDoNotLogin.addActionListener(actionListener);

    ActionListener listener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final IcsSettings settings1 = IcsManager.getInstance().getIdeaServerSettings();
      }
    };
    myLoginSilently.addActionListener(listener);
    myDoNotLogin.addActionListener(listener);
    myShowDialog.addActionListener(listener);
  }

  private static void requestCredentials(final String failedMessage, boolean isStartupMode) {
    if (isStartupMode) {
      LoginDialog dialog = new LoginDialog(failedMessage);
      dialog.setModalityType(Dialog.ModalityType.TOOLKIT_MODAL);
      AppUIUtil.updateWindowIcon(dialog);
      dialog.setVisible(true);
    }
    else {
      new LoginDialogWrapper(failedMessage).show();
      //if (getIdeaServerSettings().getStatus() == IdeaConfigurationServerStatus.LOGGED_IN) {
      //  updateConfigFilesFromServer();
      //}
    }
  }

  private void update() {
    myStatusLabel.setText("Current status: " + IcsManager.getInstance().getStatusText());
    if (myParent != null) {
      myParent.pack();
    }
  }

  public JComponent getPanel() {
    return myPanel;
  }
}
