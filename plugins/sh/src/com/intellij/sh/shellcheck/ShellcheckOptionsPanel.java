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
import java.util.List;
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
  private BiConsumer<String, Boolean> myInspectionsChangeListener;
  private ShSettings mySettings;
  private Project myProject;

  ShellcheckOptionsPanel(List<String> disabledInspections, BiConsumer<String, Boolean> inspectionsChangeListener) {
    myInspectionsChangeListener = inspectionsChangeListener;
    mySettings = ShSettings.getInstance();
    myProject = ProjectUtil.guessCurrentProject(getPanel());

    myShellcheckSelector.addBrowseFolderListener(BROWSE_SHELLCHECK_TITLE, "", myProject, FileChooserDescriptorFactory.createSingleFileDescriptor());
    myShellcheckSelector.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent documentEvent) {
        String shellcheckPath = myShellcheckSelector.getText();
        mySettings.setShellcheckPath(shellcheckPath);
        myWarningPanel.setVisible(!ShShellcheckUtil.isValidPath(shellcheckPath));
      }
    });

    String shellcheckPath = mySettings.getShellcheckPath();
    myShellcheckSelector.setText(shellcheckPath);
    myShellcheckSelector.setEditable(false);
    myWarningPanel.setVisible(!ShShellcheckUtil.isValidPath(shellcheckPath));

    disabledInspections.forEach(setting -> {
      String value = ShShellcheckUtil.shellCheckCodes.get(setting);
      if (value != null) {
        myInspectionsCheckboxPanel.addCheckbox(setting + " " + value, setting);
      }
    });

    myWarningLabel.setIcon(AllIcons.General.Warning);
  }

  private void createUIComponents() {
    myShellcheckDownloadLink = new ActionLink(LINK_TITLE, new AnAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent event) {
        ShShellcheckUtil.download(event.getProject(), () -> myShellcheckSelector.setText(ShSettings.getInstance().getShellcheckPath()));
        EditorNotifications.getInstance(myProject).updateAllNotifications();
      }
    });

    myInspectionsCheckboxPanel = new MultipleCheckboxOptionsPanel(new OptionAccessor() {
      @Override
      public boolean getOption(String optionName) {
        return true;
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
