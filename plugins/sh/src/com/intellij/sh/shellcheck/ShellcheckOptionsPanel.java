// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.shellcheck;

import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.codeInspection.ui.OptionAccessor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.sh.settings.ShSettings;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.EditorNotifications;
import com.intellij.ui.components.labels.ActionLink;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.util.Set;
import java.util.function.BiConsumer;

public class ShellcheckOptionsPanel {
  private static final String BROWSE_SHELLCHECK_TITLE = "Choose Path to the Shellcheck:";
  private static final String LINK_TITLE = "Download Shellcheck";

  private JPanel myPanel;
  private JPanel myWarningPanel;
  private JLabel myWarningLabel;
  @SuppressWarnings("unused")
  private ActionLink myShellcheckDownloadLink;
  private TextFieldWithBrowseButton myShellcheckSelector;
  private MultipleCheckboxOptionsPanel myInspectionsCheckboxPanel;
  private final BiConsumer<String, Boolean> myInspectionsChangeListener;
  private final Set<String> myDisabledInspections;
  private final Project myProject;

  ShellcheckOptionsPanel(Set<String> disabledInspections, BiConsumer<String, Boolean> inspectionsChangeListener) {
    myInspectionsChangeListener = inspectionsChangeListener;
    myDisabledInspections = disabledInspections;
    myProject = ProjectUtil.guessCurrentProject(getPanel());

    myShellcheckSelector.addBrowseFolderListener(BROWSE_SHELLCHECK_TITLE, "", myProject, FileChooserDescriptorFactory.createSingleFileDescriptor());
    myShellcheckSelector.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent documentEvent) {
        String shellcheckPath = myShellcheckSelector.getText();
        ShSettings.setShellcheckPath(shellcheckPath);
        myWarningPanel.setVisible(!ShShellcheckUtil.isValidPath(shellcheckPath));
      }
    });

    String shellcheckPath = ShSettings.getShellcheckPath();
    myShellcheckSelector.setText(shellcheckPath);
    myWarningPanel.setVisible(!ShShellcheckUtil.isValidPath(shellcheckPath));

    ShShellcheckUtil.shellCheckCodes.forEach((key, value) -> myInspectionsCheckboxPanel.addCheckbox(key + " " + value, key));
    myWarningLabel.setIcon(AllIcons.General.Warning);
  }

  private void createUIComponents() {
    myShellcheckDownloadLink = new ActionLink(LINK_TITLE, new AnAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent event) {
        ShShellcheckUtil.download(event.getProject(), () -> myShellcheckSelector.setText(ShSettings.getShellcheckPath()));
        EditorNotifications.getInstance(myProject).updateAllNotifications();
      }
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

  JComponent getPanel() {
    return myPanel;
  }
}
