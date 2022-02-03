// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal;

import com.intellij.execution.configuration.EnvironmentVariablesTextFieldWithBrowseButton;
import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.ide.DataManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextComponentAccessors;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.terminal.TerminalUiSettingsManager;
import com.intellij.ui.*;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBTextField;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.ui.SwingHelper;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.plaf.basic.BasicComboBoxEditor;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class TerminalSettingsPanel {
  private JPanel myWholePanel;
  private TextFieldWithHistoryWithBrowseButton myShellPathField;
  private JBCheckBox mySoundBellCheckBox;
  private JBCheckBox myCloseSessionCheckBox;
  private JBCheckBox myMouseReportCheckBox;
  private JTextField myTabNameTextField;
  private JBCheckBox myPasteOnMiddleButtonCheckBox;
  private JBCheckBox myCopyOnSelectionCheckBox;
  private JBCheckBox myOverrideIdeShortcuts;

  private JBCheckBox myShellIntegration;
  private TextFieldWithBrowseButton myStartDirectoryField;
  private JPanel myProjectSettingsPanel;
  private JPanel myGlobalSettingsPanel;
  private JPanel myConfigurablesPanel;
  private JBCheckBox myHighlightHyperlinks;

  private EnvironmentVariablesTextFieldWithBrowseButton myEnvVarField;
  private ActionLink myConfigureTerminalKeybindingsActionLink;
  private ComboBox<TerminalUiSettingsManager.CursorShape> myCursorShape;
  private JBCheckBox myUseOptionAsMetaKey;

  private TerminalOptionsProvider myOptionsProvider;
  private TerminalProjectOptionsProvider myProjectOptionsProvider;

  private final java.util.List<UnnamedConfigurable> myConfigurables = new ArrayList<>();

  public JComponent createPanel(@NotNull TerminalOptionsProvider provider, @NotNull TerminalProjectOptionsProvider projectOptionsProvider) {
    myOptionsProvider = provider;
    myProjectOptionsProvider = projectOptionsProvider;

    myProjectSettingsPanel.setBorder(IdeBorderFactory.createTitledBorder(TerminalBundle.message("settings.terminal.project.settings")));
    myGlobalSettingsPanel.setBorder(IdeBorderFactory.createTitledBorder(TerminalBundle.message("settings.terminal.application.settings")));

    configureShellPathField();
    configureStartDirectoryField();

    List<Component> customComponents = new ArrayList<>();
    for (LocalTerminalCustomizer c : LocalTerminalCustomizer.EP_NAME.getExtensions()) {
      UnnamedConfigurable configurable = c.getConfigurable(projectOptionsProvider.getProject());
      if (configurable != null) {
        myConfigurables.add(configurable);
        JComponent component = configurable.createComponent();
        if (component != null) {
          customComponents.add(component);
        }
      }
    }
    if (!customComponents.isEmpty()) {
      myConfigurablesPanel.setLayout(new GridLayoutManager(customComponents.size(), 1));
      int i = 0;
      for (Component component : customComponents) {
        myConfigurablesPanel.add(component, new GridConstraints(
          i++, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, 0, 0,
          new Dimension(-1, -1),
          new Dimension(-1, -1),
          new Dimension(-1, -1),
          0, false
        ));
      }
    }

    myUseOptionAsMetaKey.getParent().setVisible(SystemInfo.isMac);

    return myWholePanel;
  }

  private void configureStartDirectoryField() {
    myStartDirectoryField.addBrowseFolderListener(
      "",
      TerminalBundle.message("settings.start.directory.browseFolder.description"),
      null,
      FileChooserDescriptorFactory.createSingleFolderDescriptor(),
      TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT
    );
    setupTextFieldDefaultValue(myStartDirectoryField.getTextField(), () -> myProjectOptionsProvider.getDefaultStartingDirectory());
  }

  private void configureShellPathField() {
    myShellPathField.addBrowseFolderListener(
      "",
      TerminalBundle.message("settings.terminal.shell.executable.path.browseFolder.description"),
      null,
      FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor(),
      TextComponentAccessors.TEXT_FIELD_WITH_HISTORY_WHOLE_TEXT
    );
    setupTextFieldDefaultValue(myShellPathField.getChildComponent().getTextEditor(), () -> myProjectOptionsProvider.defaultShellPath());
  }

  private void setupTextFieldDefaultValue(@NotNull JTextField textField, @NotNull Supplier<@NlsSafe String> defaultValueSupplier) {
    String defaultShellPath = defaultValueSupplier.get();
    if (StringUtil.isEmptyOrSpaces(defaultShellPath)) return;
    textField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        textField.setForeground(defaultShellPath.equals(textField.getText()) ? getDefaultValueColor() : getChangedValueColor());
      }
    });
    if (textField instanceof JBTextField) {
      ((JBTextField)textField).getEmptyText().setText(defaultShellPath);
    }
  }

  public boolean isModified() {
    return !Objects.equals(myShellPathField.getText(), myProjectOptionsProvider.getShellPath())
           || !Objects.equals(myStartDirectoryField.getText(), StringUtil.notNullize(myProjectOptionsProvider.getStartingDirectory()))
           || !Objects.equals(myTabNameTextField.getText(), myOptionsProvider.getTabName())
           || (myCloseSessionCheckBox.isSelected() != myOptionsProvider.getCloseSessionOnLogout())
           || (myMouseReportCheckBox.isSelected() != myOptionsProvider.getMouseReporting())
           || (mySoundBellCheckBox.isSelected() != myOptionsProvider.getAudibleBell())
           || (myCopyOnSelectionCheckBox.isSelected() != myOptionsProvider.getCopyOnSelection())
           || (myPasteOnMiddleButtonCheckBox.isSelected() != myOptionsProvider.getPasteOnMiddleMouseButton())
           || (myOverrideIdeShortcuts.isSelected() != myOptionsProvider.getOverrideIdeShortcuts())
           || (myShellIntegration.isSelected() != myOptionsProvider.getShellIntegration())
           || (myHighlightHyperlinks.isSelected() != myOptionsProvider.getHighlightHyperlinks())
           || (myUseOptionAsMetaKey.isSelected() != myOptionsProvider.getUseOptionAsMetaKey())
           || myConfigurables.stream().anyMatch(c -> c.isModified())
           || !Comparing.equal(myEnvVarField.getData(), myProjectOptionsProvider.getEnvData())
           || myCursorShape.getItem() != myOptionsProvider.getCursorShape();
  }

  public void apply() {
    myProjectOptionsProvider.setStartingDirectory(myStartDirectoryField.getText());
    myProjectOptionsProvider.setShellPath(myShellPathField.getText());
    myOptionsProvider.setTabName(myTabNameTextField.getText());
    myOptionsProvider.setCloseSessionOnLogout(myCloseSessionCheckBox.isSelected());
    myOptionsProvider.setMouseReporting(myMouseReportCheckBox.isSelected());
    myOptionsProvider.setAudibleBell(mySoundBellCheckBox.isSelected());
    myOptionsProvider.setCopyOnSelection(myCopyOnSelectionCheckBox.isSelected());
    myOptionsProvider.setPasteOnMiddleMouseButton(myPasteOnMiddleButtonCheckBox.isSelected());
    myOptionsProvider.setOverrideIdeShortcuts(myOverrideIdeShortcuts.isSelected());
    myOptionsProvider.setShellIntegration(myShellIntegration.isSelected());
    myOptionsProvider.setHighlightHyperlinks(myHighlightHyperlinks.isSelected());
    myOptionsProvider.setUseOptionAsMetaKey(myUseOptionAsMetaKey.isSelected());
    myConfigurables.forEach(c -> {
      try {
        c.apply();
      }
      catch (ConfigurationException e) {
        //pass
      }
    });
    myProjectOptionsProvider.setEnvData(myEnvVarField.getData());
    myOptionsProvider.setCursorShape(ObjectUtils.notNull(myCursorShape.getItem(), TerminalUiSettingsManager.CursorShape.BLOCK));
  }

  public void reset() {
    myShellPathField.setText(myProjectOptionsProvider.getShellPath());
    myStartDirectoryField.setText(myProjectOptionsProvider.getStartingDirectory());
    myTabNameTextField.setText(myOptionsProvider.getTabName());
    myCloseSessionCheckBox.setSelected(myOptionsProvider.getCloseSessionOnLogout());
    myMouseReportCheckBox.setSelected(myOptionsProvider.getMouseReporting());
    mySoundBellCheckBox.setSelected(myOptionsProvider.getAudibleBell());
    myCopyOnSelectionCheckBox.setSelected(myOptionsProvider.getCopyOnSelection());
    myPasteOnMiddleButtonCheckBox.setSelected(myOptionsProvider.getPasteOnMiddleMouseButton());
    myOverrideIdeShortcuts.setSelected(myOptionsProvider.getOverrideIdeShortcuts());
    myShellIntegration.setSelected(myOptionsProvider.getShellIntegration());
    myHighlightHyperlinks.setSelected(myOptionsProvider.getHighlightHyperlinks());
    myUseOptionAsMetaKey.setSelected(myOptionsProvider.getUseOptionAsMetaKey());
    myConfigurables.forEach(c -> c.reset());
    myEnvVarField.setData(myProjectOptionsProvider.getEnvData());
    myCursorShape.setItem(myOptionsProvider.getCursorShape());
  }

  public Color getDefaultValueColor() {
    return findColorByKey("TextField.inactiveForeground", "nimbusDisabledText");
  }

  private void createUIComponents() {
    myConfigureTerminalKeybindingsActionLink = new ActionLink("", e -> {
        Settings settings = DataManager.getInstance().getDataContext((ActionLink)e.getSource()).getData(Settings.KEY);
        if (settings != null) {
          Configurable configurable = settings.find("preferences.keymap");
          settings.select(configurable, "Terminal").doWhenDone(() -> {
            // Remove once https://youtrack.jetbrains.com/issue/IDEA-212247 is fixed
            EdtExecutorService.getScheduledExecutorInstance().schedule(() -> {
              settings.select(configurable, "Terminal");
            }, 100, TimeUnit.MILLISECONDS);
          });
        }
    });
    UIUtil.applyStyle(UIUtil.ComponentStyle.SMALL, myConfigureTerminalKeybindingsActionLink);
    myCursorShape = new ComboBox<>(TerminalUiSettingsManager.CursorShape.values());
    myCursorShape.setRenderer(SimpleListCellRenderer.create((label, value, index) -> {
      label.setText(value.getText());
    }));
    myShellPathField = createShellPath();
  }

  private @NotNull TextFieldWithHistoryWithBrowseButton createShellPath() {
    TextFieldWithHistoryWithBrowseButton textFieldWithHistoryWithBrowseButton = new TextFieldWithHistoryWithBrowseButton();
    final TextFieldWithHistory textFieldWithHistory = textFieldWithHistoryWithBrowseButton.getChildComponent();
    textFieldWithHistory.setEditor(new BasicComboBoxEditor() {
      @Override
      protected JTextField createEditorComponent() {
        return new JBTextField();
      }
    });
    textFieldWithHistory.setHistorySize(-1);
    textFieldWithHistory.setMinimumAndPreferredWidth(0);
    SwingHelper.addHistoryOnExpansion(textFieldWithHistory, () -> {
      return detectShells();
    });
    return textFieldWithHistoryWithBrowseButton;
  }

  private @NotNull List<String> detectShells() {
    List<String> shells = new ArrayList<>();
    if (SystemInfo.isUnix) {
      addIfExists(shells, "/bin/bash");
      addIfExists(shells, "/usr/bin/zsh");
      addIfExists(shells, "/usr/local/bin/zsh");
      addIfExists(shells, "/usr/bin/fish");
      addIfExists(shells, "/usr/local/bin/fish");
    }
    else if (SystemInfo.isWindows) {
      File powershell = PathEnvironmentVariableUtil.findInPath("powershell.exe");
      if (powershell != null && StringUtil.startsWithIgnoreCase(powershell.getAbsolutePath(), "C:\\Windows\\System32\\WindowsPowerShell\\")) {
        shells.add(powershell.getAbsolutePath());
      }
      File cmd = PathEnvironmentVariableUtil.findInPath("cmd.exe");
      if (cmd != null && StringUtil.startsWithIgnoreCase(cmd.getAbsolutePath(), "C:\\Windows\\System32\\")) {
        shells.add(cmd.getAbsolutePath());
      }
      File pwsh = PathEnvironmentVariableUtil.findInPath("pwsh.exe");
      if (pwsh != null && StringUtil.startsWithIgnoreCase(pwsh.getAbsolutePath(), "C:\\Program Files\\PowerShell\\")) {
        shells.add(pwsh.getAbsolutePath());
      }
      File gitBash = new File("C:\\Program Files\\Git\\bin\\bash.exe");
      if (gitBash.isFile()) {
        shells.add(gitBash.getAbsolutePath());
      }
      String cmderRoot = EnvironmentUtil.getValue("CMDER_ROOT");
      if (cmderRoot == null) {
        cmderRoot = myEnvVarField.getEnvs().get("CMDER_ROOT");
      }
      if (cmderRoot != null && cmd != null && StringUtil.startsWithIgnoreCase(cmd.getAbsolutePath(), "C:\\Windows\\System32\\")) {
        shells.add("cmd.exe /k \"%CMDER_ROOT%\\vendor\\init.bat\"");
      }
    }
    return shells;
  }

  private static void addIfExists(@NotNull List<String> shells, @NotNull String filePath) {
    if (Files.exists(Path.of(filePath))) {
      shells.add(filePath);
    }
  }

  @NotNull
  private static Color findColorByKey(String... colorKeys) {
    Color c = null;
    for (String key : colorKeys) {
      c = UIManager.getColor(key);
      if (c != null) {
        break;
      }
    }

    assert c != null : "Can't find color for keys " + Arrays.toString(colorKeys);
    return c;
  }

  public Color getChangedValueColor() {
    return findColorByKey("TextField.foreground");
  }
}
