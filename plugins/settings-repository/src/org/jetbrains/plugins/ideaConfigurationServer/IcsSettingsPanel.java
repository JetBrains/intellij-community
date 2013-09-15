package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class IcsSettingsPanel extends DialogWrapper {
  private JPanel panel;
  private JTextField urlTextField;
  private JCheckBox updateRepositoryFromRemoteCheckBox;
  private JCheckBox shareProjectWorkspaceCheckBox;
  private final JButton syncButton;

  public IcsSettingsPanel() {
    super(true);

    IcsManager icsManager = IcsManager.getInstance();
    IcsSettings settings = icsManager.getSettings();

    updateRepositoryFromRemoteCheckBox.setSelected(settings.updateOnStart);
    shareProjectWorkspaceCheckBox.setSelected(settings.shareProjectWorkspace);
    urlTextField.setText(icsManager.getRepositoryManager().getRemoteRepositoryUrl());

    syncButton = new JButton("Sync now\u2026");
    syncButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        saveRemoteRepositoryUrl();
        IcsManager.getInstance().sync().doWhenDone(new Runnable() {
          @Override
          public void run() {
            Messages.showInfoMessage(getContentPane(), IcsBundle.message("sync.done.message"), IcsBundle.message("sync.done.title"));
          }
        }).doWhenRejected(new Consumer<String>() {
          @Override
          public void consume(String error) {
            Messages.showErrorDialog(getContentPane(), IcsBundle.message("sync.rejected.message", StringUtil.notNullize(error, "Internal error")), IcsBundle.message("sync.rejected.title"));
          }
        });
      }
    });
    updateSyncButtonState();

    urlTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        updateSyncButtonState();
      }
    });

    setTitle(IcsManager.PLUGIN_NAME + " Settings");
    setResizable(false);
    init();
  }

  private void updateSyncButtonState() {
    syncButton.setEnabled(!StringUtil.isEmptyOrSpaces(urlTextField.getText()));
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return urlTextField;
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return panel;
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    return new Action[]{getOKAction()};
  }

  @Override
  protected void doOKAction() {
    apply();
    super.doOKAction();
  }

  @Nullable
  @Override
  protected JComponent createSouthPanel() {
    JComponent southPanel = super.createSouthPanel();
    assert southPanel != null;
    southPanel.add(syncButton, BorderLayout.WEST);
    return southPanel;
  }

  private void apply() {
    IcsSettings settings = IcsManager.getInstance().getSettings();
    settings.updateOnStart = updateRepositoryFromRemoteCheckBox.isSelected();
    settings.shareProjectWorkspace = shareProjectWorkspaceCheckBox.isSelected();
    saveRemoteRepositoryUrl();
  }

  private void saveRemoteRepositoryUrl() {
    IcsManager.getInstance().getRepositoryManager().setRemoteRepositoryUrl(StringUtil.nullize(urlTextField.getText()));
  }
}