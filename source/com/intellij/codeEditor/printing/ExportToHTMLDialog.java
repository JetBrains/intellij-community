package com.intellij.codeEditor.printing;

import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.ui.OptionGroup;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ExportToHTMLDialog extends DialogWrapper {
  private JRadioButton myRbCurrentFile;
  private JRadioButton myRbSelectedText;
  private JRadioButton myRbCurrentPackage;
  private JCheckBox myCbIncludeSubpackages;
  private JCheckBox myCbLineNumbers;
  private JCheckBox myCbGenerateHyperlinksToClasses;
  private JCheckBox myCbOpenInBrowser;
  private TextFieldWithBrowseButton myTargetDirectoryField;
  private String myFileName;
  private String myDirectoryName;
  private boolean myIsSelectedTextEnabled;
  private Project myProject;

  public ExportToHTMLDialog(String fileName, String directoryName, boolean isSelectedTextEnabled, Project project) {
    super(project, true);
    myProject = project;
    setOKButtonText("Save");
    myFileName = fileName;
    myDirectoryName = directoryName;
    this.myIsSelectedTextEnabled = isSelectedTextEnabled;
    setTitle("Export to HTML");
    init();
  }

  protected JComponent createNorthPanel() {
    OptionGroup optionGroup = new OptionGroup();

    myRbCurrentFile = new JRadioButton("File " + (myFileName != null ? myFileName : ""));
    myRbCurrentFile.setMnemonic('F');
    optionGroup.add(myRbCurrentFile);

    myRbSelectedText = new JRadioButton("Selected text");
    myRbSelectedText.setMnemonic('S');
    optionGroup.add(myRbSelectedText);

    myRbCurrentPackage = new JRadioButton("All files in directory "+ (myDirectoryName != null ? myDirectoryName : ""));
    myRbCurrentPackage.setMnemonic('d');
    optionGroup.add(myRbCurrentPackage);

    myCbIncludeSubpackages = new JCheckBox("Include subdirectories ");
    myCbIncludeSubpackages.setMnemonic('I');
    optionGroup.add(myCbIncludeSubpackages, true);

    myTargetDirectoryField = new TextFieldWithBrowseButton();
    LabeledComponent<TextFieldWithBrowseButton> labeledComponent = assignLabel(myTargetDirectoryField, myProject);

    optionGroup.add(labeledComponent);

    ButtonGroup buttonGroup = new ButtonGroup();
    buttonGroup.add(myRbCurrentFile);
    buttonGroup.add(myRbSelectedText);
    buttonGroup.add(myRbCurrentPackage);

    ActionListener actionListener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myCbIncludeSubpackages.setEnabled(myRbCurrentPackage.isSelected());
      }
    };

    myRbCurrentFile.addActionListener(actionListener);
    myRbSelectedText.addActionListener(actionListener);
    myRbCurrentPackage.addActionListener(actionListener);

    return optionGroup.createPanel();
  }

  public static LabeledComponent<TextFieldWithBrowseButton> assignLabel(TextFieldWithBrowseButton targetDirectoryField, Project project) {
    LabeledComponent<TextFieldWithBrowseButton> labeledComponent = new LabeledComponent<TextFieldWithBrowseButton>();
    labeledComponent.setText("&Output directory:");
    targetDirectoryField.addBrowseFolderListener("Select output directory",
                                                   "HTML files will be exported to this directory",
                                                 project, FileChooserDescriptorFactory.createSingleFolderDescriptor());
    labeledComponent.setComponent(targetDirectoryField);
    labeledComponent.setBorder(BorderFactory.createEmptyBorder(5, 4, 0, 0));
    return labeledComponent;
  }

  protected JComponent createCenterPanel() {
    OptionGroup optionGroup = new OptionGroup("Options");

    myCbLineNumbers = new JCheckBox("Show line numbers");
    myCbLineNumbers.setMnemonic('l');
    optionGroup.add(myCbLineNumbers);


    myCbGenerateHyperlinksToClasses = new JCheckBox("Generate hyperlinks to classes");
    myCbGenerateHyperlinksToClasses.setMnemonic('h');
    optionGroup.add(myCbGenerateHyperlinksToClasses);

    myCbOpenInBrowser = new JCheckBox("Open generated HTML in browser");
    myCbOpenInBrowser.setMnemonic('b');
    optionGroup.add(myCbOpenInBrowser);

    return optionGroup.createPanel();
  }

  public void reset() {
    ExportToHTMLSettings exportToHTMLSettings = ExportToHTMLSettings.getInstance(myProject);

    myRbSelectedText.setEnabled(myIsSelectedTextEnabled);
    myRbSelectedText.setSelected(myIsSelectedTextEnabled);
    myRbCurrentFile.setEnabled(myFileName != null);
    myRbCurrentFile.setSelected(myFileName != null && !myIsSelectedTextEnabled);
    myRbCurrentPackage.setEnabled(myDirectoryName != null);
    myRbCurrentPackage.setSelected(myDirectoryName != null && !myIsSelectedTextEnabled && myFileName == null);
    myCbIncludeSubpackages.setSelected(exportToHTMLSettings.isIncludeSubdirectories());
    myCbIncludeSubpackages.setEnabled(myRbCurrentPackage.isSelected());

    myCbLineNumbers.setSelected(exportToHTMLSettings.PRINT_LINE_NUMBERS);
    myCbOpenInBrowser.setSelected(exportToHTMLSettings.OPEN_IN_BROWSER);
    myCbGenerateHyperlinksToClasses.setSelected(exportToHTMLSettings.isGenerateHyperlinksToClasses());

    myTargetDirectoryField.setText(exportToHTMLSettings.OUTPUT_DIRECTORY);
  }

  public void apply() {
    ExportToHTMLSettings exportToHTMLSettings = ExportToHTMLSettings.getInstance(myProject);

    if (myRbCurrentFile.isSelected()){
      exportToHTMLSettings.setPrintScope(PrintSettings.PRINT_FILE);
    }
    else if (myRbSelectedText.isSelected()){
      exportToHTMLSettings.setPrintScope(PrintSettings.PRINT_SELECTED_TEXT);
    }
    else if (myRbCurrentPackage.isSelected()){
      exportToHTMLSettings.setPrintScope(PrintSettings.PRINT_DIRECTORY);
    }

    exportToHTMLSettings.setIncludeSubpackages(myCbIncludeSubpackages.isSelected());
    exportToHTMLSettings.PRINT_LINE_NUMBERS = myCbLineNumbers.isSelected();
    exportToHTMLSettings.OPEN_IN_BROWSER = myCbOpenInBrowser.isSelected();
    exportToHTMLSettings.OUTPUT_DIRECTORY = myTargetDirectoryField.getText();
    exportToHTMLSettings.setGenerateHyperlinksToClasses(myCbGenerateHyperlinksToClasses.isSelected());
  }
}