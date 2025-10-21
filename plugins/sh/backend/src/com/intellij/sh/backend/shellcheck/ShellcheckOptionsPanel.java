// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.backend.shellcheck;

import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.codeInspection.ui.OptionAccessor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.sh.ShBundle;
import com.intellij.sh.settings.ShSettings;
import com.intellij.sh.shellcheck.ShShellcheckUtil;
import com.intellij.sh.utils.ProjectUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.EditorNotifications;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.ActionLink;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.util.Set;
import java.util.function.BiConsumer;

public class ShellcheckOptionsPanel {
  private JPanel myPanel;
  private JPanel myWarningPanel;
  private JLabel myWarningLabel;
  private JLabel myErrorLabel;
  @SuppressWarnings("unused")
  private ActionLink myShellcheckDownloadLink;
  private TextFieldWithBrowseButton myShellcheckSelector;
  private MultipleCheckboxOptionsPanel myInspectionsCheckboxPanel;
  private final BiConsumer<String, Boolean> myInspectionsChangeListener;
  private final Set<String> myDisabledInspections;
  private final Project myProject;

  @ApiStatus.Internal
  public ShellcheckOptionsPanel(Set<String> disabledInspections, BiConsumer<String, Boolean> inspectionsChangeListener) {
    myInspectionsChangeListener = inspectionsChangeListener;
    myDisabledInspections = disabledInspections;
    myProject = ProjectUtil.getProject(getPanel());

    myShellcheckSelector.addBrowseFolderListener(myProject, FileChooserDescriptorFactory.createSingleFileDescriptor()
      .withTitle(ShBundle.message("sh.shellcheck.path.label")));
    myShellcheckSelector.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent documentEvent) {
        String shellcheckPath = myShellcheckSelector.getText();
        ShSettings.setShellcheckPath(myProject, shellcheckPath);
        myWarningPanel.setVisible(!ShShellcheckUtil.isValidPath(shellcheckPath));
        myErrorLabel.setVisible(false);
      }
    });

    String shellcheckPath = ShSettings.getShellcheckPath(myProject);
    myShellcheckSelector.setText(shellcheckPath);
    myWarningPanel.setVisible(!ShShellcheckUtil.isValidPath(shellcheckPath));
    myErrorLabel.setForeground(JBColor.RED);

    ShShellcheckUtil.SHELLCHECK_CODES.forEach(
      (@NlsSafe String key, @Nls String value) -> myInspectionsCheckboxPanel.addCheckbox(key + " " + value, key));
    myWarningLabel.setIcon(AllIcons.General.Warning);
  }

  private void createUIComponents() {
    myShellcheckDownloadLink = new ActionLink(ShBundle.message("sh.shellcheck.download.label.text"), e -> {
        ShShellcheckUtil.download(myProject,
                                  () -> myShellcheckSelector.setText(ShSettings.getShellcheckPath(myProject)),
                                  () -> myErrorLabel.setVisible(true));
        EditorNotifications.getInstance(myProject).updateAllNotifications();
    });

    myInspectionsCheckboxPanel = new MultipleCheckboxOptionsPanel(new OptionAccessor() {
      @Override
      public boolean getOption(String optionName) {
        return myDisabledInspections.contains(optionName);
      }

      @Override
      public void setOption(String optionName, boolean optionValue) {
        myInspectionsChangeListener.accept(optionName, optionValue);
      }
    });
  }

  @ApiStatus.Internal
  public JComponent getPanel() {
    return myPanel;
  }
}
