package com.intellij.execution.junit2.configuration;

import com.intellij.execution.RunJavaConfiguration;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.RawCommandLineEditor;

import javax.swing.*;
import java.awt.*;

public class CommonJavaParameters extends JPanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.junit2.configuration.CommonJavaParameters");
  private static final int[] ourProperties = new int[]{
    RunJavaConfiguration.PROGRAM_PARAMETERS_PROPERTY,
    RunJavaConfiguration.VM_PARAMETERS_PROPERTY,
    RunJavaConfiguration.WORKING_DIRECTORY_PROPERTY
  };

  private JPanel myWholePanel;
  private LabeledComponent<TextFieldWithBrowseButton> myWorkingDirectory;
  private LabeledComponent<RawCommandLineEditor> myProgramParameters;
  private LabeledComponent<RawCommandLineEditor> myVMParameters;

  private LabeledComponent[] myFields = new LabeledComponent[3];

  public CommonJavaParameters() {
    super(new BorderLayout());
    add(myWholePanel, BorderLayout.CENTER);
    copyDialogCaption(myProgramParameters);
    copyDialogCaption(myVMParameters);
    myWorkingDirectory.getComponent().
      addBrowseFolderListener("Select Working Directory", null, null,
                              FileChooserDescriptorFactory.createSingleFolderDescriptor());

    myFields[RunJavaConfiguration.PROGRAM_PARAMETERS_PROPERTY] = myProgramParameters;
    myFields[RunJavaConfiguration.VM_PARAMETERS_PROPERTY] = myVMParameters;
    myFields[RunJavaConfiguration.WORKING_DIRECTORY_PROPERTY] = myWorkingDirectory;
  }

  private void copyDialogCaption(final LabeledComponent<RawCommandLineEditor> component) {
    final RawCommandLineEditor rawCommandLineEditor = component.getComponent();
    rawCommandLineEditor.setDialodCaption(component.getRawText());
    component.getLabel().setLabelFor(rawCommandLineEditor.getTextField());
  }

  public String getProgramParametersText() {
    return getLabeledComponent(RunJavaConfiguration.PROGRAM_PARAMETERS_PROPERTY).getText();
  }

  public void setProgramParametersText(String textWithMnemonic) {
    getLabeledComponent(RunJavaConfiguration.PROGRAM_PARAMETERS_PROPERTY).setText(textWithMnemonic);
    copyDialogCaption(myProgramParameters);
  }

  public void applyTo(final RunJavaConfiguration configuration) {
    for (int i = 0; i < ourProperties.length; i++) {
      final int property = ourProperties[i];
      configuration.setProperty(property, getText(property));
    }
  }

  public void reset(final RunJavaConfiguration configuration) {
    for (int i = 0; i < ourProperties.length; i++) {
      final int property = ourProperties[i];
      setText(property, configuration.getProperty(property));
    }
  }

  public void setText(final int property, final String value) {
    final JComponent component = getLabeledComponent(property).getComponent();
    if (component instanceof TextFieldWithBrowseButton)
      ((TextFieldWithBrowseButton)component).setText(value);
    else if (component instanceof RawCommandLineEditor)
      ((RawCommandLineEditor)component).setText(value);
    else LOG.error(component.getClass().getName());
  }

  public String getText(final int property) {
    final JComponent component = getLabeledComponent(property).getComponent();
    if (component instanceof TextFieldWithBrowseButton)
      return ((TextFieldWithBrowseButton)component).getText();
    else if (component instanceof RawCommandLineEditor)
      return ((RawCommandLineEditor)component).getText();
    else LOG.error(component.getClass().getName());
    return "";
  }

  private LabeledComponent getLabeledComponent(final int index) {
    return myFields[index];
  }
}
