package org.jetbrains.settingsRepository;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.ArrayUtil;
import kotlin.Function0;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.settingsRepository.actions.ActionsPackage;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionEvent;

public class IcsSettingsPanel extends DialogWrapper {
  private JPanel panel;
  private TextFieldWithBrowseButton urlTextField;
  private final Action[] syncActions;
  private final IcsManager icsManager;

  public IcsSettingsPanel(@Nullable final Project project) {
    super(project, true);

    icsManager = IcsManager.OBJECT$.getInstance();
    //shareProjectWorkspaceCheckBox.setSelected(settings.getShareProjectWorkspace());
    urlTextField.setText(icsManager.getRepositoryManager().getUpstream());
    urlTextField.addBrowseFolderListener(new TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleFolderDescriptor()));

    SyncType[] syncTypes = SyncType.values();
    if (SystemInfo.isMac) {
      syncTypes = ArrayUtil.reverseArray(syncTypes);
    }

    syncActions = new Action[syncTypes.length];
    for (int i = 0, n = syncTypes.length; i < n; i++) {
      final SyncType syncType = syncTypes[i];
      syncActions[i] = new DialogWrapperAction(IcsBundle.OBJECT$.message(
        "action." + (syncType == SyncType.MERGE ? "Merge" : (syncType == SyncType.RESET_TO_THEIRS ? "ResetToTheirs" : "ResetToMy")) + "Settings.text")) {
        @Override
        protected void doAction(ActionEvent event) {
          boolean repositoryWillBeCreated = !icsManager.getRepositoryManager().isRepositoryExists();
          if (!saveRemoteRepositoryUrl(syncType)) {
            if (repositoryWillBeCreated) {
              // remove created repository
              icsManager.getRepositoryManager().deleteRepository();
            }
            return;
          }

          try {
            if (repositoryWillBeCreated && syncType != SyncType.RESET_TO_THEIRS) {
              ApplicationManager.getApplication().saveSettings();

              icsManager.sync(syncType, project, new Function0<Unit>() {
                @Override
                public Unit invoke() {
                  SettingsRepositoryPackage.copyLocalConfig();
                  return Unit.INSTANCE$;
                }
              });
            }
            else {
              icsManager.sync(syncType, project, null);
            }
          }
          catch (Throwable e) {
            if (repositoryWillBeCreated) {
              // remove created repository
              icsManager.getRepositoryManager().deleteRepository();
            }

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
    String url = StringUtil.nullize(urlTextField.getText());
    boolean enabled;
    try {
      enabled = url != null && url.length() > 1 && icsManager.getRepositoryService().checkUrl(url, null);
    }
    catch (Exception e) {
      enabled = false;
    }

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

  private boolean saveRemoteRepositoryUrl(@NotNull SyncType syncType) {
    String url = StringUtil.nullize(urlTextField.getText());
    if (url != null) {
      try {
        if (!icsManager.getRepositoryService().checkUrl(url, getContentPane())) {
          return false;
        }
      }
      catch (Throwable e) {
        return false;
      }
    }

    try {
      RepositoryManager repositoryManager = icsManager.getRepositoryManager();
      repositoryManager.createRepositoryIfNeed();
      repositoryManager.setUpstream(url, null);
      return true;
    }
    catch (Throwable e) {
      SettingsRepositoryPackage.getLOG().warn(e);
      Messages.showErrorDialog(getContentPane(), IcsBundle.OBJECT$.message("set.upstream.failed.message", e.getMessage()), IcsBundle.OBJECT$.message("set.upstream.failed.title"));
      return false;
    }
  }
}
