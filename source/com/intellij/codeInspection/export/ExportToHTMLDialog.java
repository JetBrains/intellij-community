package com.intellij.codeInspection.export;

import com.intellij.codeEditor.printing.ExportToHTMLSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.OptionGroup;

import javax.swing.*;

// TODO copy-paste result of com.intellij.codeEditor.printing.ExportToHTMLDialog
public class ExportToHTMLDialog extends DialogWrapper{
  private JCheckBox myCbOpenInBrowser;
  private final Project myProject;
  private TextFieldWithBrowseButton myTargetDirectoryField;

  public ExportToHTMLDialog(Project project) {
    super(project, true);
    myProject = project;
    setOKButtonText("Save");
    setTitle("Export to HTML");
    init();
  }

  protected JComponent createNorthPanel() {
    OptionGroup optionGroup = new OptionGroup();

    myTargetDirectoryField = new TextFieldWithBrowseButton();
    optionGroup.add(com.intellij.codeEditor.printing.ExportToHTMLDialog.assignLabel(myTargetDirectoryField, myProject));

    return optionGroup.createPanel();
  }

  protected JComponent createCenterPanel() {
    OptionGroup optionGroup = new OptionGroup("Options");

    myCbOpenInBrowser = new JCheckBox("Open generated HTML in browser");
    myCbOpenInBrowser.setMnemonic('b');
    optionGroup.add(myCbOpenInBrowser);

    return optionGroup.createPanel();
  }

  public void reset() {
    ExportToHTMLSettings exportToHTMLSettings = ExportToHTMLSettings.getInstance(myProject);
    myCbOpenInBrowser.setSelected(exportToHTMLSettings.OPEN_IN_BROWSER);
    myTargetDirectoryField.setText(exportToHTMLSettings.OUTPUT_DIRECTORY);
  }

  public void apply() {
    ExportToHTMLSettings exportToHTMLSettings = ExportToHTMLSettings.getInstance(myProject);

    exportToHTMLSettings.OPEN_IN_BROWSER = myCbOpenInBrowser.isSelected();
    exportToHTMLSettings.OUTPUT_DIRECTORY = myTargetDirectoryField.getText();
  }
}