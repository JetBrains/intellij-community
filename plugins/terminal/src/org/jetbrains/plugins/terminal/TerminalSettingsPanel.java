// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal;

import com.intellij.execution.configuration.EnvironmentVariablesTextFieldWithBrowseButton;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.labels.ActionLink;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class TerminalSettingsPanel {
  private JPanel myWholePanel;
  private TextFieldWithBrowseButton myShellPathField;
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

  private TerminalOptionsProvider myOptionsProvider;
  private TerminalProjectOptionsProvider myProjectOptionsProvider;

  private final java.util.List<UnnamedConfigurable> myConfigurables = new ArrayList<>();

  public JComponent createPanel(@NotNull TerminalOptionsProvider provider, @NotNull TerminalProjectOptionsProvider projectOptionsProvider) {
    myOptionsProvider = provider;
    myProjectOptionsProvider = projectOptionsProvider;

    myProjectSettingsPanel.setBorder(IdeBorderFactory.createTitledBorder(IdeBundle.message("settings.terminal.project.settings")));
    myGlobalSettingsPanel.setBorder(IdeBorderFactory.createTitledBorder(IdeBundle.message("settings.terminal.application.settings")));

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

    return myWholePanel;
  }

  private void configureStartDirectoryField() {
    myStartDirectoryField.addBrowseFolderListener(
      "",
      "Starting directory",
      null,
      FileChooserDescriptorFactory.createSingleFolderDescriptor(),
      TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT
    );
    setupTextFieldDefaultValue(myStartDirectoryField.getTextField(), () -> myProjectOptionsProvider.getDefaultStartingDirectory());
  }

  private void configureShellPathField() {
    myShellPathField.addBrowseFolderListener(
      "",
      IdeBundle.message("settings.terminal.shell.executable.path"),
      null,
      FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor(),
      TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT
    );
    setupTextFieldDefaultValue(myShellPathField.getTextField(), () -> myProjectOptionsProvider.defaultShellPath());
  }

  private void setupTextFieldDefaultValue(@NotNull JTextField textField, @NotNull Supplier<String> defaultValueSupplier) {
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
           || (myCloseSessionCheckBox.isSelected() != myOptionsProvider.closeSessionOnLogout())
           || (myMouseReportCheckBox.isSelected() != myOptionsProvider.enableMouseReporting())
           || (mySoundBellCheckBox.isSelected() != myOptionsProvider.audibleBell())
           || (myCopyOnSelectionCheckBox.isSelected() != myOptionsProvider.copyOnSelection())
           || (myPasteOnMiddleButtonCheckBox.isSelected() != myOptionsProvider.pasteOnMiddleMouseButton())
           || (myOverrideIdeShortcuts.isSelected() != myOptionsProvider.overrideIdeShortcuts())
           || (myShellIntegration.isSelected() != myOptionsProvider.shellIntegration())
           || (myHighlightHyperlinks.isSelected() != myOptionsProvider.highlightHyperlinks()) ||
           myConfigurables.stream().anyMatch(c -> c.isModified())
           || !Comparing.equal(myEnvVarField.getData(), myProjectOptionsProvider.getEnvData());
  }

  public void apply() {
    myProjectOptionsProvider.setStartingDirectory(myStartDirectoryField.getText());
    myProjectOptionsProvider.setShellPath(myShellPathField.getText());
    myOptionsProvider.setTabName(myTabNameTextField.getText());
    myOptionsProvider.setCloseSessionOnLogout(myCloseSessionCheckBox.isSelected());
    myOptionsProvider.setReportMouse(myMouseReportCheckBox.isSelected());
    myOptionsProvider.setSoundBell(mySoundBellCheckBox.isSelected());
    myOptionsProvider.setCopyOnSelection(myCopyOnSelectionCheckBox.isSelected());
    myOptionsProvider.setPasteOnMiddleMouseButton(myPasteOnMiddleButtonCheckBox.isSelected());
    myOptionsProvider.setOverrideIdeShortcuts(myOverrideIdeShortcuts.isSelected());
    myOptionsProvider.setShellIntegration(myShellIntegration.isSelected());
    myOptionsProvider.setHighlightHyperlinks(myHighlightHyperlinks.isSelected());
    myConfigurables.forEach(c -> {
      try {
        c.apply();
      }
      catch (ConfigurationException e) {
        //pass
      }
    });
    myProjectOptionsProvider.setEnvData(myEnvVarField.getData());
  }

  public void reset() {
    myShellPathField.setText(myProjectOptionsProvider.getShellPath());
    myStartDirectoryField.setText(myProjectOptionsProvider.getStartingDirectory());
    myTabNameTextField.setText(myOptionsProvider.getTabName());
    myCloseSessionCheckBox.setSelected(myOptionsProvider.closeSessionOnLogout());
    myMouseReportCheckBox.setSelected(myOptionsProvider.enableMouseReporting());
    mySoundBellCheckBox.setSelected(myOptionsProvider.audibleBell());
    myCopyOnSelectionCheckBox.setSelected(myOptionsProvider.copyOnSelection());
    myPasteOnMiddleButtonCheckBox.setSelected(myOptionsProvider.pasteOnMiddleMouseButton());
    myOverrideIdeShortcuts.setSelected(myOptionsProvider.overrideIdeShortcuts());
    myShellIntegration.setSelected(myOptionsProvider.shellIntegration());
    myHighlightHyperlinks.setSelected(myOptionsProvider.highlightHyperlinks());
    myConfigurables.forEach(c -> c.reset());
    myEnvVarField.setData(myProjectOptionsProvider.getEnvData());
  }

  public Color getDefaultValueColor() {
    return findColorByKey("TextField.inactiveForeground", "nimbusDisabledText");
  }

  private void createUIComponents() {
    myConfigureTerminalKeybindingsActionLink = new ActionLink(null, new DumbAwareAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        Settings settings = e.getData(Settings.KEY);
        if (settings != null) {
          Configurable configurable = settings.find("preferences.keymap");
          settings.select(configurable, "Terminal").doWhenDone(() -> {
            // Remove once https://youtrack.jetbrains.com/issue/IDEA-212247 is fixed
            EdtExecutorService.getScheduledExecutorInstance().schedule(() -> {
              settings.select(configurable, "Terminal");
            }, 100, TimeUnit.MILLISECONDS);
          });
        }
      }
    });
    UIUtil.applyStyle(UIUtil.ComponentStyle.SMALL, myConfigureTerminalKeybindingsActionLink);
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
