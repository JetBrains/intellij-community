package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBLabel;

import javax.swing.*;
import javax.swing.event.DocumentEvent;

public class IcsSettingsPanel {
  private JPanel panel;
  private JTextField tokenField;
  private JTextField urlTextField;
  private JCheckBox updateRepositoryFromRemoteCheckBox;
  private JCheckBox shareProjectWorkspaceCheckBox;
  private JBLabel tokenFieldLabel;

  public IcsSettingsPanel() {
    IcsManager icsManager = IcsManager.getInstance();
    IcsSettings settings = icsManager.getSettings();

    tokenField.setText(settings.token);
    updateRepositoryFromRemoteCheckBox.setSelected(settings.updateOnStart);
    shareProjectWorkspaceCheckBox.setSelected(settings.shareProjectWorkspace);
    urlTextField.setText(icsManager.getRepositoryManager().getRemoteRepositoryUrl());

    urlTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        boolean tokenFieldEnabled = isHttps();
        tokenField.setEnabled(tokenFieldEnabled);
        tokenFieldLabel.setEnabled(tokenFieldEnabled);
      }
    });
  }

  private boolean isHttps() {
    return urlTextField.getText().startsWith("https://");
  }

  public JComponent getPanel() {
    return panel;
  }

  public void apply() {
    IcsManager icsManager = IcsManager.getInstance();
    IcsSettings settings = icsManager.getSettings();
    settings.token = tokenField.getText();
    settings.updateOnStart = updateRepositoryFromRemoteCheckBox.isSelected();
    settings.shareProjectWorkspace = shareProjectWorkspaceCheckBox.isSelected();
    icsManager.getRepositoryManager().setRemoteRepositoryUrl(StringUtil.nullize(urlTextField.getText()));
  }

  public ValidationInfo doValidate() {
    if (isHttps() && StringUtil.isEmptyOrSpaces(tokenField.getText())) {
      return new ValidationInfo("Token is empty", tokenField);
    }
    return null;
  }
}