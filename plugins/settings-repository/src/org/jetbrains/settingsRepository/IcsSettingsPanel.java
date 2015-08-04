package org.jetbrains.settingsRepository;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;

public class IcsSettingsPanel {
  JPanel panel;
  TextFieldWithBrowseButton urlTextField;
  private final Action[] syncActions;

  public IcsSettingsPanel(@Nullable final Project project, @NotNull Container dialogParent, @NotNull Function0<Unit> okAction) {
    urlTextField.setText(SettingsRepositoryPackage.getIcsManager().getRepositoryManager().getUpstream());
    urlTextField.addBrowseFolderListener(new TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleFolderDescriptor()));

    syncActions = SettingsRepositoryPackage.createMergeActions(project, urlTextField, dialogParent, okAction);

    urlTextField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        SettingsRepositoryPackage.updateSyncButtonState(StringUtil.nullize(urlTextField.getText()), syncActions);
      }
    });

    urlTextField.requestFocusInWindow();
    SettingsRepositoryPackage.updateSyncButtonState(StringUtil.nullize(urlTextField.getText()), syncActions);
  }

  @NotNull
  Action[] createActions() {
    return syncActions;
  }
}