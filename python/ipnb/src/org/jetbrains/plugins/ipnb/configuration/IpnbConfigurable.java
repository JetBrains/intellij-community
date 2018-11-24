package org.jetbrains.plugins.ipnb.configuration;

import com.intellij.ide.DataManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ClickListener;
import com.intellij.ui.UI;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.PlatformUtils;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.configuration.PyActiveSdkModuleConfigurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseEvent;

public class IpnbConfigurable implements SearchableConfigurable {
  private JPanel myMainPanel;
  private JBTextField myUsernameField;
  private JLabel myInterpreterSetupLinkLabel;
  private JPasswordField myPasswordField;
  private JPanel myProPanel;
  private JCheckBox myMarkdownCheckBox;

  private final Project myProject;

  public IpnbConfigurable(@NotNull Project project) {
    myProject = project;
    myProPanel.setVisible(PlatformUtils.isPyCharmPro() || PlatformUtils.isIdeaUltimate());
    myInterpreterSetupLinkLabel.setForeground(UI.getColor("link.foreground"));
    myInterpreterSetupLinkLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
    createNavigateToInterpreterSettingsListener().installOn(myInterpreterSetupLinkLabel);

    myUsernameField.addFocusListener(createInitialTextFocusAdapter(myUsernameField, DEFAULT_USERNAME_TEXT));
    setInitialText(myUsernameField, IpnbSettings.getInstance(myProject).getUsername(), DEFAULT_USERNAME_TEXT);
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Jupyter Notebook";
  }

  @Override
  public String getHelpTopic() {
    return "reference-ipnb";
  }

  @Override
  public JComponent createComponent() {
    return myMainPanel;
  }

  @NotNull
  @Override
  public String getId() {
    return "IpnbConfigurable";
  }

  private static final String DEFAULT_USERNAME_TEXT = "Leave empty for a single-user notebook";

  @NotNull
  private ClickListener createNavigateToInterpreterSettingsListener() {
    return new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent event, int clickCount) {
        final Settings settings = Settings.KEY.getData(DataManager.getInstance().getDataContext(myMainPanel));
        if (settings != null) {
          settings.select(settings.find(PyActiveSdkModuleConfigurable.class.getName()));
          return true;
        }
        return false;
      }
    };
  }

  public void apply() {
    final IpnbSettings ipnbSettings = IpnbSettings.getInstance(myProject);
    ipnbSettings.setHasFx(myMarkdownCheckBox.isSelected());
    for (FileEditor editor : FileEditorManager.getInstance(myProject).getAllEditors()) {
      if (editor instanceof IpnbFileEditor) {
        final VirtualFile file = ((IpnbFileEditor)editor).getVirtualFile();
        FileEditorManager.getInstance(myProject).closeFile(file);
        FileEditorManager.getInstance(myProject).openFile(file, false);
      }
    }
    if (myProPanel.isVisible()) {
      final String oldUsername = ipnbSettings.getUsername();
      final String oldPassword = ipnbSettings.getPassword(myProject.getLocationHash());

      final String newUsername = getUsername();
      final String newPassword = String.valueOf(myPasswordField.getPassword());

      if (!oldUsername.equals(newUsername) || !oldPassword.equals(newPassword)) {
        IpnbConnectionManager.getInstance(myProject).shutdownKernels();
        ipnbSettings.setUsername(newUsername);
        ipnbSettings.setPassword(newPassword, myProject.getLocationHash());
      }
    }
  }

  public void reset() {
    final IpnbSettings ipnbSettings = IpnbSettings.getInstance(myProject);
    final boolean hasFx = ipnbSettings.hasFx();
    myMarkdownCheckBox.setSelected(hasFx);
    if (myProPanel.isVisible()) {
      final String savedUsername = ipnbSettings.getUsername();
      setInitialText(myUsernameField, savedUsername, DEFAULT_USERNAME_TEXT);

      final String savedPassword = ipnbSettings.getPassword(myProject.getLocationHash());
      myPasswordField.setText(savedPassword);
    }
  }

  public boolean isModified() {
    final IpnbSettings ipnbSettings = IpnbSettings.getInstance(myProject);
    final boolean hasFx = ipnbSettings.hasFx();
    if (hasFx != myMarkdownCheckBox.isSelected()) return true;

    if (myProPanel.isVisible()) {
      final String oldUsername = ipnbSettings.getUsername();
      final String oldPassword = ipnbSettings.getPassword(myProject.getLocationHash());

      final String newPassword = String.valueOf(myPasswordField.getPassword());
      final String newUsername = getUsername();

      return !oldPassword.equals(newPassword) || !oldUsername.equals(newUsername);
    }
    return false;
  }

  private String getUsername() {
    final String usernameText = myUsernameField.getText();
    return DEFAULT_USERNAME_TEXT.equals(usernameText) ? "" : usernameText;
  }

  @NotNull
  private static FocusAdapter createInitialTextFocusAdapter(@NotNull JBTextField field, @NotNull String initialText) {
    return new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        if (field.getText().equals(initialText)) {
          field.setForeground(UIUtil.getActiveTextColor());
          field.setText("");
        }
      }

      @Override
      public void focusLost(FocusEvent e) {
        if (field.getText().isEmpty()) {
          field.setForeground(UIUtil.getInactiveTextColor());
          field.setText(initialText);
        }
      }
    };
  }

  private static void setInitialText(@NotNull JBTextField field,
                                     @NotNull String savedValue,
                                     @NotNull String defaultText) {
    if (savedValue.isEmpty()) {
      field.setForeground(UIUtil.getInactiveTextColor());
      field.setText(defaultText);
    }
    else {
      field.setForeground(UIUtil.getActiveTextColor());
      field.setText(savedValue);
    }
  }
}

