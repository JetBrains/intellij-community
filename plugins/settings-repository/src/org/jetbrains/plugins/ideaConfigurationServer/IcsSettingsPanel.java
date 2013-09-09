package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.openapi.util.text.StringUtil;

import javax.swing.*;

public class IcsSettingsPanel {
  private JPanel panel;
  private JTextField loginTextField;
  private JTextField tokenTextField;
  private JTextField urlTextField;
  private JCheckBox updateRepositoryFromRemoteCheckBox;
  private JCheckBox shareProjectWorkspaceCheckBox;

  public IcsSettingsPanel() {
    IcsManager icsManager = IcsManager.getInstance();
    IcsSettings settings = icsManager.getSettings();

    loginTextField.setText(settings.getLogin());
    tokenTextField.setText(settings.token);
    updateRepositoryFromRemoteCheckBox.setSelected(settings.updateOnStart);
    shareProjectWorkspaceCheckBox.setSelected(settings.shareProjectWorkspace);
    urlTextField.setText(icsManager.getRepositoryManager().getRemoteRepositoryUrl());
  }

  public JComponent getPanel() {
    return panel;
  }

  public void apply() {
    IcsManager icsManager = IcsManager.getInstance();
    IcsSettings settings = icsManager.getSettings();
    settings.update(loginTextField.getText(), tokenTextField.getText());
    settings.token = tokenTextField.getText();
    settings.updateOnStart = updateRepositoryFromRemoteCheckBox.isSelected();
    settings.shareProjectWorkspace = shareProjectWorkspaceCheckBox.isSelected();
    icsManager.getRepositoryManager().setRemoteRepositoryUrl(StringUtil.nullize(urlTextField.getText()));
  }
}