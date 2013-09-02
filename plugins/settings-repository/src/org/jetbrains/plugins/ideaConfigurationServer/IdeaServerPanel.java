package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.ui.DialogWrapper;

import javax.swing.*;
import javax.swing.border.TitledBorder;
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
    final IdeaServerSettings settings = getSettings();

    final TitledBorder border = (TitledBorder)myStartupPanel.getBorder();
    border.setTitle("On next " + ApplicationNamesInfo.getInstance().getProductName() + " startup");

    myActionButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        try {
          if (settings.getStatus() == IdeaServerStatus.LOGGED_IN) {
            IdeaServerManagerImpl.getInstance().logout();
          }
          else {
            IdeaServerManagerImpl.getInstance().requestCredentials(null, false);
          }
        }
        finally {
          update();
        }
      }
    });

    update();

    ActionListener actionListener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        IdeaServerSettings settings = getSettings();
        settings.REMEMBER_SETTINGS = myLoginSilently.isSelected() || myDoNotLogin.isSelected();
        settings.DO_LOGIN = myLoginSilently.isSelected() || myShowDialog.isSelected();
      }
    };
    myLoginSilently.addActionListener(actionListener);
    myShowDialog.addActionListener(actionListener);
    myDoNotLogin.addActionListener(actionListener);

    if (settings.REMEMBER_SETTINGS && settings.DO_LOGIN) {
      myLoginSilently.setSelected(true);
    }
    else if (settings.REMEMBER_SETTINGS && !settings.DO_LOGIN) {
      myDoNotLogin.setSelected(true);
    }
    else {
      myShowDialog.setSelected(true);
    }

    ActionListener listener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        apply();
      }
    };
    myLoginSilently.addActionListener(listener);
    myDoNotLogin.addActionListener(listener);
    myShowDialog.addActionListener(listener);
  }

  private void apply() {
    final IdeaServerSettings settings = getSettings();

    if (myLoginSilently.isSelected()) {
      settings.REMEMBER_SETTINGS = true;
      settings.DO_LOGIN = true;
    }

    else if (myDoNotLogin.isSelected()) {
      settings.REMEMBER_SETTINGS = true;
      settings.DO_LOGIN = false;
    }

    else if (myShowDialog.isSelected()) {
      settings.REMEMBER_SETTINGS = false;
      settings.DO_LOGIN = true;
    }
  }

  private IdeaServerSettings getSettings() {
    return IdeaServerManagerImpl.getInstance().getIdeaServerSettings();
  }


  private void update() {
    myStatusLabel.setText("Current status: " + IdeaServerManagerImpl.getStatusText());
    if (IdeaServerManagerImpl.getInstance().getIdeaServerSettings().getStatus() == IdeaServerStatus.LOGGED_IN) {
      myActionButton.setText("Logout");
    }
    else {
      myActionButton.setText("Login...");
    }

    if (myParent != null) {
      myParent.pack();
    }
  }

  public JComponent getPanel() {
    return myPanel;
  }
}
