package com.intellij.execution.application;

import com.intellij.execution.junit2.configuration.ClassBrowser;
import com.intellij.execution.junit2.configuration.CommonJavaParameters;
import com.intellij.execution.junit2.configuration.ConfigurationModuleSelector;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;

import javax.swing.*;

public class ApplicationConfigurable2 extends SettingsEditor<ApplicationConfiguration>{
  private CommonJavaParameters myCommonJavaParameters;
  private LabeledComponent<TextFieldWithBrowseButton> myMainClass;
  private LabeledComponent<JComboBox> myModule;
  private JPanel myWholePanel;

  private final ConfigurationModuleSelector myModuleSelector;

  public ApplicationConfigurable2(final Project project) {
    myModuleSelector = new ConfigurationModuleSelector(project, myModule.getComponent());
    ClassBrowser.createApplicationClassBrowser(project, myModuleSelector).setField(getMainClassField());
  }

  public void applyEditorTo(final ApplicationConfiguration configuration){
    myCommonJavaParameters.applyTo(configuration);
    myModuleSelector.applyTo(configuration);
    configuration.MAIN_CLASS_NAME = getMainClassField().getText();
  }

  public void resetEditorFrom(final ApplicationConfiguration configuration) {
    myCommonJavaParameters.reset(configuration);
    myModuleSelector.reset(configuration);
    getMainClassField().setText(configuration.MAIN_CLASS_NAME);
  }

  public TextFieldWithBrowseButton getMainClassField() {
    return myMainClass.getComponent();
  }

  public CommonJavaParameters getCommonJavaParameters() {
    return myCommonJavaParameters;
  }

  public JComponent createEditor() {
    return myWholePanel;
  }

  public void disposeEditor() {
  }

  public String getHelpTopic() {
    return null;
  }
}
