// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal;

import com.google.common.collect.Lists;
import com.intellij.execution.configuration.EnvironmentVariablesTextFieldWithBrowseButton;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

/**
 * @author traff
 */
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

  private TerminalOptionsProvider myOptionsProvider;
  private TerminalProjectOptionsProvider myProjectOptionsProvider;

  private final java.util.List<UnnamedConfigurable> myConfigurables = Lists.newArrayList();

  public JComponent createPanel(@NotNull TerminalOptionsProvider provider, @NotNull TerminalProjectOptionsProvider projectOptionsProvider) {
    myOptionsProvider = provider;
    myProjectOptionsProvider = projectOptionsProvider;

    myProjectSettingsPanel.setBorder(IdeBorderFactory.createTitledBorder("Project settings"));
    myGlobalSettingsPanel.setBorder(IdeBorderFactory.createTitledBorder("Application settings"));

    FileChooserDescriptor fileChooserDescriptor = new FileChooserDescriptor(true, false, false, false, false, false);

    myShellPathField.addBrowseFolderListener(
      "",
      "Shell executable path",
      null,
      fileChooserDescriptor,
      TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);

    fileChooserDescriptor = new FileChooserDescriptor(false, true, false, false, false, false);

    myStartDirectoryField.addBrowseFolderListener(
      "",
      "Starting directory",
      null,
      fileChooserDescriptor,
      TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);

    myShellPathField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        myShellPathField
          .getTextField().setForeground(StringUtil.equals(myShellPathField.getText(), myProjectOptionsProvider.getDefaultShellPath()) ?
                                        getDefaultValueColor() : getChangedValueColor());
      }
    });

    myStartDirectoryField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        myStartDirectoryField
          .getTextField()
          .setForeground(StringUtil.equals(myStartDirectoryField.getText(), myProjectOptionsProvider.getDefaultStartingDirectory()) ?
                         getDefaultValueColor() : getChangedValueColor());
      }
    });

    List<Component> customComponents = ContainerUtil.newArrayList();
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

  public boolean isModified() {
    return !Comparing.equal(myShellPathField.getText(), myOptionsProvider.getShellPath())
           || !Comparing.equal(myStartDirectoryField.getText(), StringUtil.notNullize(myProjectOptionsProvider.getStartingDirectory()))
           || !Comparing.equal(myTabNameTextField.getText(), myOptionsProvider.getTabName())
           || (myCloseSessionCheckBox.isSelected() != myOptionsProvider.closeSessionOnLogout())
           || (myMouseReportCheckBox.isSelected() != myOptionsProvider.enableMouseReporting())
           || (mySoundBellCheckBox.isSelected() != myOptionsProvider.audibleBell())
           || (myCopyOnSelectionCheckBox.isSelected() != myOptionsProvider.copyOnSelection())
           || (myPasteOnMiddleButtonCheckBox.isSelected() != myOptionsProvider.pasteOnMiddleMouseButton())
           || (myOverrideIdeShortcuts.isSelected() != myOptionsProvider.overrideIdeShortcuts())
           || (myShellIntegration.isSelected() != myOptionsProvider.shellIntegration())
           || (myHighlightHyperlinks.isSelected() != myOptionsProvider.highlightHyperlinks()) ||
           myConfigurables.stream().anyMatch(c -> c.isModified())
           || !Comparing.equal(myEnvVarField.getData(), myOptionsProvider.getEnvData());
  }

  public void apply() {
    myProjectOptionsProvider.setStartingDirectory(myStartDirectoryField.getText());
    myOptionsProvider.setShellPath(myShellPathField.getText());
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
    myOptionsProvider.setEnvData(myEnvVarField.getData());
  }

  public void reset() {
    myShellPathField.setText(myOptionsProvider.getShellPath());
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
    myEnvVarField.setData(myOptionsProvider.getEnvData());
  }

  public Color getDefaultValueColor() {
    return findColorByKey("TextField.inactiveForeground", "nimbusDisabledText");
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
