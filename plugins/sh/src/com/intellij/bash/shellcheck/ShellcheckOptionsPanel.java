package com.intellij.bash.shellcheck;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.labels.ActionLink;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;

public class ShellcheckOptionsPanel {
  private static final String BROWSE_SHELLCHECK_TITLE = "Choose Path to the Shellcheck:";
  private static final String LINK_TITLE = "Download Shellcheck";

  private JPanel myPanel;
  private JPanel myWarningPanel;
  private JLabel myWarningLabel;
  @SuppressWarnings("unused")
  private ActionLink myShellcheckDownloadLink;
  private TextFieldWithBrowseButton myShellcheckSelector;

  ShellcheckOptionsPanel() {
    Project project = ProjectUtil.guessCurrentProject(getPanel());
    myShellcheckSelector.addBrowseFolderListener(BROWSE_SHELLCHECK_TITLE, "", project, FileChooserDescriptorFactory.createSingleFileDescriptor());
    myShellcheckSelector.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent documentEvent) {
        String shellcheckPath = myShellcheckSelector.getText();
        ShShellcheckUtil.setShellcheckPath(shellcheckPath);
        myWarningPanel.setVisible(!ShShellcheckUtil.isValidPath(shellcheckPath));
      }
    });
    myShellcheckSelector.setText(ShShellcheckUtil.getShellcheckPath());
    myShellcheckSelector.setEditable(false);

    myWarningLabel.setIcon(AllIcons.General.Warning);
  }

  private void createUIComponents() {
    myShellcheckDownloadLink = new ActionLink(LINK_TITLE, new AnAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent event) {
        ShShellcheckUtil.download(event.getProject(), getPanel());
        myShellcheckSelector.setText(ShShellcheckUtil.getShellcheckPath());
      }
    });
  }

  JComponent getPanel() {
    return myPanel;
  }
}
