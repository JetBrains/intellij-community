// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal;

import com.intellij.execution.configuration.EnvironmentVariablesTextFieldWithBrowseButton;
import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.impl.TrustedProjects;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextComponentAccessors;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.terminal.TerminalUiSettingsManager;
import com.intellij.ui.*;
import com.intellij.ui.components.*;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.SwingHelper;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.terminal.block.BlockTerminalOptions;
import org.jetbrains.plugins.terminal.block.TerminalUsageLocalStorage;
import org.jetbrains.plugins.terminal.block.feedback.BlockTerminalFeedbackSurveyKt;
import org.jetbrains.plugins.terminal.block.prompt.TerminalPromptStyle;
import org.jetbrains.plugins.terminal.fus.BlockTerminalSwitchPlace;
import org.jetbrains.plugins.terminal.fus.TerminalUsageTriggerCollector;

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

public final class TerminalSettingsPanel {
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

  private JPanel myNewUiSettingsPanel;
  private JBCheckBox myNewUiCheckbox;
  private JBLabel myBetaLabel;
  private JPanel myNewUiChildSettingsPanel;

  private JPanel myPromptStyleButtonsPanel;
  private JBRadioButton mySingleLineButton;
  private JBRadioButton myDoubleLineButton;
  private JBRadioButton myShellPromptButton;
  private JPanel myShellPromptButtonPanel;
  private JPanel myNewUiConfigurablesPanel;

  private Project myProject;
  private TerminalOptionsProvider myOptionsProvider;
  private TerminalProjectOptionsProvider myProjectOptionsProvider;
  private BlockTerminalOptions myBlockTerminalOptions;

  private final List<UnnamedConfigurable> myConfigurables = new ArrayList<>();
  private final List<UnnamedConfigurable> myNewUiConfigurables = new ArrayList<>();

  public JComponent createPanel(
    @NotNull Project project,
    @NotNull TerminalOptionsProvider provider,
    @NotNull TerminalProjectOptionsProvider projectOptionsProvider,
    @NotNull BlockTerminalOptions blockTerminalOptions
  ) {
    myProject = project;
    myOptionsProvider = provider;
    myProjectOptionsProvider = projectOptionsProvider;
    myBlockTerminalOptions = blockTerminalOptions;

    myNewUiSettingsPanel.setVisible(ExperimentalUI.isNewUI());
    myBetaLabel.setIcon(AllIcons.General.Beta);
    myNewUiChildSettingsPanel.setBorder(JBUI.Borders.emptyLeft(28));
    myNewUiCheckbox.setSelected(Registry.is(LocalBlockTerminalRunner.BLOCK_TERMINAL_REGISTRY));
    myNewUiCheckbox.addChangeListener(__ -> updateNewUiPanelState());

    myPromptStyleButtonsPanel.setBorder(JBUI.Borders.empty(4, 20, 0, 0));
    // UI Designer is unable to create a ContextHelpLabel, because it doesn't have a default constructor.
    // So, I have to create and add it manually.
    var shellPromptDescription = ContextHelpLabel.create(TerminalBundle.message("settings.shell.prompt.description"));
    shellPromptDescription.setBorder(JBUI.Borders.emptyLeft(6));
    myShellPromptButtonPanel.add(shellPromptDescription);

    var buttonGroup = new ButtonGroup();
    buttonGroup.add(mySingleLineButton);
    buttonGroup.add(myDoubleLineButton);
    buttonGroup.add(myShellPromptButton);

    myProjectSettingsPanel.setBorder(IdeBorderFactory.createTitledBorder(TerminalBundle.message("settings.terminal.project.settings")));
    myGlobalSettingsPanel.setBorder(IdeBorderFactory.createTitledBorder(TerminalBundle.message("settings.terminal.application.settings")));

    configureShellPathField();
    configureStartDirectoryField();

    for (LocalTerminalCustomizer c : LocalTerminalCustomizer.EP_NAME.getExtensionList()) {
      UnnamedConfigurable configurable = c.getConfigurable(projectOptionsProvider.getProject());
      if (configurable != null) {
        myConfigurables.add(configurable);
      }

      UnnamedConfigurable newUiConfigurable = c.getBlockTerminalConfigurable(projectOptionsProvider.getProject());
      if (newUiConfigurable != null) {
        myNewUiConfigurables.add(newUiConfigurable);
      }
    }

    addCustomConfigurablesToPanel(myConfigurablesPanel, myConfigurables);
    addCustomConfigurablesToPanel(myNewUiConfigurablesPanel, myNewUiConfigurables);

    // Show child New Terminal settings as disabled if New Terminal is not selected
    updateNewUiPanelState();

    myUseOptionAsMetaKey.getParent().setVisible(SystemInfo.isMac);

    return myWholePanel;
  }

  private void configureStartDirectoryField() {
    var descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor().withDescription(TerminalBundle.message("settings.start.directory.browseFolder.description"));
    myStartDirectoryField.addBrowseFolderListener(null, descriptor, TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);
    setupTextFieldDefaultValue(myStartDirectoryField.getTextField(), () -> myProjectOptionsProvider.getDefaultStartingDirectory());
  }

  private void configureShellPathField() {
    var descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor().withDescription(TerminalBundle.message("settings.terminal.shell.executable.path.browseFolder.description"));
    myShellPathField.addBrowseFolderListener(null, descriptor, TextComponentAccessors.TEXT_FIELD_WITH_HISTORY_WHOLE_TEXT);
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

  private void updateNewUiPanelState() {
    UIUtil.uiTraverser(myNewUiChildSettingsPanel).forEach(c -> {
      c.setEnabled(myNewUiCheckbox.isSelected());
    });
  }

  public boolean isModified() {
    return myNewUiCheckbox.isSelected() != Registry.is(LocalBlockTerminalRunner.BLOCK_TERMINAL_REGISTRY)
           || (myBlockTerminalOptions.getPromptStyle() != getSelectedPromptStyle())
           || !Objects.equals(myShellPathField.getText(), myProjectOptionsProvider.getShellPath())
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
           || ContainerUtil.exists(myConfigurables, c -> c.isModified())
           || ContainerUtil.exists(myNewUiConfigurables, c -> c.isModified())
           || !Comparing.equal(myEnvVarField.getData(), myProjectOptionsProvider.getEnvData())
           || myCursorShape.getItem() != myOptionsProvider.getCursorShape();
  }

  public void apply() {
    var blockTerminalSetting = Registry.get(LocalBlockTerminalRunner.BLOCK_TERMINAL_REGISTRY);
    if (blockTerminalSetting.asBoolean() != myNewUiCheckbox.isSelected()) {
      blockTerminalSetting.setValue(myNewUiCheckbox.isSelected());
      TerminalUsageTriggerCollector.triggerBlockTerminalSwitched$intellij_terminal(myProject, myNewUiCheckbox.isSelected(),
                                                                                   BlockTerminalSwitchPlace.SETTINGS);
      if (!myNewUiCheckbox.isSelected()) {
        TerminalUsageLocalStorage.getInstance().recordBlockTerminalDisabled();
        ApplicationManager.getApplication().invokeLater(() -> {
          BlockTerminalFeedbackSurveyKt.showBlockTerminalFeedbackNotification(myProject);
        }, ModalityState.nonModal());
      }
    }
    myBlockTerminalOptions.setPromptStyle(getSelectedPromptStyle());
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
    myConfigurables.forEach(c -> applyIgnoringConfigurationException(c));
    myNewUiConfigurables.forEach(c -> applyIgnoringConfigurationException(c));
    myProjectOptionsProvider.setEnvData(myEnvVarField.getData());
    myOptionsProvider.setCursorShape(ObjectUtils.notNull(myCursorShape.getItem(), TerminalUiSettingsManager.CursorShape.BLOCK));
  }

  private static void applyIgnoringConfigurationException(@NotNull UnnamedConfigurable configurable) {
    try {
      configurable.apply();
    }
    catch (ConfigurationException e) {
      // pass
    }
  }

  public void reset() {
    myNewUiCheckbox.setSelected(Registry.is(LocalBlockTerminalRunner.BLOCK_TERMINAL_REGISTRY));
    var promptStyle = myBlockTerminalOptions.getPromptStyle();
    mySingleLineButton.setSelected(promptStyle == TerminalPromptStyle.SINGLE_LINE);
    myDoubleLineButton.setSelected(promptStyle == TerminalPromptStyle.DOUBLE_LINE);
    myShellPromptButton.setSelected(promptStyle == TerminalPromptStyle.SHELL);
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
    myNewUiConfigurables.forEach(c -> c.reset());
    myEnvVarField.setData(myProjectOptionsProvider.getEnvData());
    myCursorShape.setItem(myOptionsProvider.getCursorShape());
    myEnvVarField.setEnabled(TrustedProjects.isTrusted(myProject));
  }

  private TerminalPromptStyle getSelectedPromptStyle() {
    if (mySingleLineButton.isSelected()) {
      return TerminalPromptStyle.SINGLE_LINE;
    }
    else if (myDoubleLineButton.isSelected()) {
      return TerminalPromptStyle.DOUBLE_LINE;
    }
    else if (myShellPromptButton.isSelected()) {
      return TerminalPromptStyle.SHELL;
    }
    throw new IllegalStateException("None of prompt style radio buttons are selected");
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

  private static void addCustomConfigurablesToPanel(@NotNull JPanel panel, @NotNull List<UnnamedConfigurable> configurables) {
    List<JComponent> components = ContainerUtil.map(configurables, it -> it.createComponent());

    if (components.isEmpty()) {
      return;
    }

    panel.setLayout(new GridLayoutManager(components.size(), 1));

    for (int i = 0; i < components.size(); i++) {
      var component = components.get(i);
      panel.add(component, new GridConstraints(
        i, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, 0, 0,
        new Dimension(-1, -1),
        new Dimension(-1, -1),
        new Dimension(-1, -1),
        0, false
      ));
    }
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
      addIfExists(shells, "/usr/bin/bash");
      addIfExists(shells, "/usr/local/bin/bash");
      addIfExists(shells, "/opt/homebrew/bin/bash");

      addIfExists(shells, "/bin/zsh");
      addIfExists(shells, "/usr/bin/zsh");
      addIfExists(shells, "/usr/local/bin/zsh");
      addIfExists(shells, "/opt/homebrew/bin/zsh");

      addIfExists(shells, "/bin/fish");
      addIfExists(shells, "/usr/bin/fish");
      addIfExists(shells, "/usr/local/bin/fish");
      addIfExists(shells, "/opt/homebrew/bin/fish");

      addIfExists(shells, "/opt/homebrew/bin/pwsh");
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
