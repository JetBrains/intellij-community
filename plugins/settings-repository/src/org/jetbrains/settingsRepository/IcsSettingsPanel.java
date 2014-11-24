package org.jetbrains.settingsRepository;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import kotlin.Function0;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.settingsRepository.actions.ActionsPackage;

import javax.swing.*;
import javax.swing.event.DocumentEvent;

public class IcsSettingsPanel extends DialogWrapper {
  private JPanel panel;
  private TextFieldWithBrowseButton urlTextField;
  private final Action[] syncActions;
  private final IcsManager icsManager;

  public IcsSettingsPanel(@Nullable final Project project) {
    super(project, true);

    icsManager = IcsManager.OBJECT$.getInstance();
    urlTextField.setText(icsManager.getRepositoryManager().getUpstream());
    urlTextField.addBrowseFolderListener(new TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleFolderDescriptor()));

    syncActions = ActionsPackage.createDialogActions(project, urlTextField, getContentPane(), new Function0<Unit>() {
      @Override
      public Unit invoke() {
        doOKAction();
        return null;
      }
    });

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
}
