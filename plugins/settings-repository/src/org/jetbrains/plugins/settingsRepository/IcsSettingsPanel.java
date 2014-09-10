package org.jetbrains.plugins.settingsRepository;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.ArrayUtil;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.settingsRepository.actions.ActionsPackage;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionEvent;

public class IcsSettingsPanel extends DialogWrapper {
  private JPanel panel;
  private TextFieldWithBrowseButton urlTextField;
  private final Action[] syncActions;

  public IcsSettingsPanel(@Nullable final Project project) {
    super(project, true);

    IcsManager icsManager = IcsManager.OBJECT$.getInstance();
    //shareProjectWorkspaceCheckBox.setSelected(settings.getShareProjectWorkspace());
    urlTextField.setText(icsManager.getRepositoryManager().getUpstream());
    urlTextField.addBrowseFolderListener(new TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleFolderDescriptor()));

    SyncType[] syncTypes = SettingsRepositoryPackage.getSYNC_TYPES();
    if (SystemInfo.isMac) {
      syncTypes = ArrayUtil.reverseArray(syncTypes);
    }

    syncActions = new Action[syncTypes.length];
    for (int i = 0, n = syncTypes.length; i < n; i++) {
      final SyncType syncType = syncTypes[i];
      syncActions[i] = new DialogWrapperAction(IcsBundle.OBJECT$.message(
        "action." + (syncType == SyncType.MERGE ? "Merge" : (syncType == SyncType.RESET_TO_THEIRS ? "ResetToTheirs" : "ResetToYours")) + "Settings.text")) {
        @Override
        protected void doAction(ActionEvent event) {
          if (!saveRemoteRepositoryUrl()) {
            return;
          }

          try {
            IcsManager.OBJECT$.getInstance().sync(syncType, project);
          }
          catch (Exception e) {
            Messages.showErrorDialog(getContentPane(), StringUtil.notNullize(e.getMessage(), "Internal error"), IcsBundle.OBJECT$.message("sync.rejected.title"));
            return;
          }

          ActionsPackage.getNOTIFICATION_GROUP().createNotification(IcsBundle.OBJECT$.message("sync.done.message"), NotificationType.INFORMATION).notify(project);
          doOKAction();
        }
      };
    }

    urlTextField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        updateSyncButtonState();
      }
    });

    updateSyncButtonState();

    setTitle(IcsBundle.OBJECT$.message("settings.panel.title"));
    setResizable(false);
    init();
  }

  private void updateSyncButtonState() {
    String url = urlTextField.getText();
    boolean enabled = !StringUtil.isEmptyOrSpaces(url) && url.length() > 1;
    for (Action syncAction : syncActions) {
      syncAction.setEnabled(enabled);
    }
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
    return syncActions;
  }

  private boolean saveRemoteRepositoryUrl() {
    String url = StringUtil.nullize(urlTextField.getText());
    if (url != null) {
      boolean isFile;
      if (url.startsWith(StandardFileSystems.FILE_PROTOCOL_PREFIX)) {
        url = url.substring(StandardFileSystems.FILE_PROTOCOL_PREFIX.length());
        isFile = true;
      }
      else {
        isFile = !url.startsWith("git@") && !URLUtil.containsScheme(url);
      }

      if (isFile && !IcsManager.OBJECT$.getInstance().getRepositoryService().checkFileRepo(url, getContentPane())) {
        return false;
      }
    }

    try {
      IcsManager.OBJECT$.getInstance().getRepositoryManager().createRepositoryIfNeed().setUpstream(url, null);
      return true;
    }
    catch (Exception e) {
      Messages.showErrorDialog(getContentPane(), IcsBundle.OBJECT$.message("set.upstream.failed.message", e.getMessage()), IcsBundle.OBJECT$.message("set.upstream.failed.title"));
      return false;
    }
  }
}
