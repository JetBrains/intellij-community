package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.io.URLUtil;
import org.eclipse.jgit.lib.Constants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;

@SuppressWarnings("DialogTitleCapitalization")
public class IcsSettingsPanel extends DialogWrapper {
  private JPanel panel;
  private TextFieldWithBrowseButton urlTextField;
  private JCheckBox updateRepositoryFromRemoteCheckBox;
  private JCheckBox shareProjectWorkspaceCheckBox;
  private final JButton syncButton;

  public IcsSettingsPanel(@Nullable final Project project) {
    super(project, true);

    IcsManager icsManager = IcsManager.getInstance();
    IcsSettings settings = icsManager.getSettings();

    updateRepositoryFromRemoteCheckBox.setSelected(settings.updateOnStart);
    shareProjectWorkspaceCheckBox.setSelected(settings.shareProjectWorkspace);
    urlTextField.setText(icsManager.getRepositoryManager().getRemoteRepositoryUrl());
    urlTextField.addBrowseFolderListener(new TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleFolderDescriptor()));

    // todo TextComponentUndoProvider should not depends on app settings
    //new TextComponentUndoProvider(urlTextField);

    syncButton = new JButton(IcsBundle.message("settings.panel.sync.repositories"));
    syncButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
        if (saveRemoteRepositoryUrl()) {
          new SyncRepositoriesDialog(panel).show();
        }
      }
    });
    updateSyncButtonState();

    urlTextField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        updateSyncButtonState();
      }
    });

    setTitle(IcsBundle.message("settings.panel.title"));
    setResizable(false);
    init();
  }

  private void updateSyncButtonState() {
    String url = urlTextField.getText();
    syncButton.setEnabled(!StringUtil.isEmptyOrSpaces(url) && url.length() > 1);
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

    ApplicationManager.getApplication().executeOnPooledThread(new Callable<Object>() {
      @Override
      public Object call() throws Exception {
        IcsManager.getInstance().getSettings().save();
        return null;
      }
    });
  }

  private boolean saveRemoteRepositoryUrl() {
    String url = StringUtil.nullize(urlTextField.getText());
    if (url != null) {
      boolean isFile;
      if (url.startsWith(StandardFileSystems.FILE_PROTOCOL_PREFIX)) {
        url = url.substring(StandardFileSystems.FILE_PROTOCOL_PREFIX.length());
        isFile = true;
      }
      else if (url.startsWith("git@") || url.startsWith("ssh://") || url.startsWith("git+ssh://")) {
        Messages.showErrorDialog(getContentPane(), "SSH URL is not supported, please use HTTP/HTTPS");
        return false;
      }
      else {
        isFile = !URLUtil.containsScheme(url);
      }

      if (isFile && !checkFileRepo(url)) {
        return false;
      }
    }
    IcsManager.getInstance().getRepositoryManager().setRemoteRepositoryUrl(url);
    return true;
  }

  private boolean checkFileRepo(@NotNull String url) {
    String suffix = '/' + Constants.DOT_GIT;
    if (url.endsWith(suffix)) {
      url = url.substring(0, url.length() - suffix.length());
    }

    File file = new File(url);
    if (file.exists()) {
      if (!file.isDirectory()) {
        Messages.showErrorDialog(getContentPane(), "Specified path is not a directory", "Specified path is invalid");
        return false;
      }
      else if (new File(file, Constants.DOT_GIT).exists()) {
        return true;
      }
    }

    if (Messages.showYesNoDialog(getContentPane(), IcsBundle.message("init.dialog.message"), IcsBundle.message("init.dialog.title"), Messages.getQuestionIcon()) == Messages.YES) {
      try {
        IcsManager.getInstance().getRepositoryManager().initRepository(file);
        return true;
      }
      catch (IOException e) {
        Messages.showErrorDialog(getContentPane(), IcsBundle.message("init.failed.message", e.getMessage()), IcsBundle.message("init.failed.title"));
        return false;
      }
    }
    else {
      return false;
    }
  }
}