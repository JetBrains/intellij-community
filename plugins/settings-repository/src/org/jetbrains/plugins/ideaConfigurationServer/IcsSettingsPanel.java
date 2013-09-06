package org.jetbrains.plugins.ideaConfigurationServer;

import javax.swing.*;

public class IcsSettingsPanel {
  private JPanel panel;
  private JTextField loginTextField;
  private JTextField tokenTextField;
  private JTextField urlTextField;
  private JCheckBox updateRepositoryFromRemoteCheckBox;
  private JCheckBox shareProjectWorkspaceCheckBox;

  public IcsSettingsPanel() {
    IcsSettings settings = IcsManager.getInstance().getIdeaServerSettings();

    loginTextField.setText(settings.getLogin());
    tokenTextField.setText(settings.token);
    urlTextField.setText(settings.url);
    updateRepositoryFromRemoteCheckBox.setSelected(settings.updateOnStart);
    shareProjectWorkspaceCheckBox.setSelected(settings.shareProjectWorkspace);
  }

  public JComponent getPanel() {
    return panel;
  }

  public void apply() {
    IcsSettings settings = IcsManager.getInstance().getIdeaServerSettings();
    settings.update(loginTextField.getText(), tokenTextField.getText());
    settings.token = tokenTextField.getText();
    settings.url = urlTextField.getText();
    settings.updateOnStart = updateRepositoryFromRemoteCheckBox.isSelected();
    settings.shareProjectWorkspace = shareProjectWorkspaceCheckBox.isSelected();
  }
}
