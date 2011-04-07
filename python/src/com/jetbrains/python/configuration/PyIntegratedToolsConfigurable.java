package com.jetbrains.python.configuration;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.NonDefaultProjectConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.CollectionComboBoxModel;
import com.jetbrains.python.documentation.DocStringFormat;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.testing.PythonTestConfigurationsModel;
import com.jetbrains.python.testing.TestRunnerService;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import java.util.List;

/**
 * User: catherine
 */
public class PyIntegratedToolsConfigurable implements Configurable, NonDefaultProjectConfigurable {
  private JPanel myMainPanel;
  private JComboBox myTestRunnerComboBox;
  private JComboBox myDocstringFormatComboBox;
  private PythonTestConfigurationsModel myModel;
  private Project myProject;
  private final PyDocumentationSettings myDocumentationSettings;

  public PyIntegratedToolsConfigurable(Project project) {
    myProject = project;
    myDocumentationSettings = PyDocumentationSettings.getInstance(project);
    myDocstringFormatComboBox.setModel(new CollectionComboBoxModel(DocStringFormat.ALL, myDocumentationSettings.myDocStringFormat));
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Python Integrated Tools";
  }

  @Override
  public Icon getIcon() {
    return null;
  }

  @Override
  public String getHelpTopic() {
    return "test_runner_configuration";
  }

  @Override
  public JComponent createComponent() {
    List<String> configurations = TestRunnerService.getInstance(myProject).getConfigurations();
    myModel = new PythonTestConfigurationsModel(configurations, TestRunnerService.getInstance(myProject).getProjectConfiguration(),
                                                myProject);
    updateConfigurations();
    return myMainPanel;
  }

  private void updateConfigurations() {
    myTestRunnerComboBox.setModel(myModel);
  }

  @Override
  public boolean isModified() {
    if (myTestRunnerComboBox.getSelectedItem() != myModel.getProjectConfiguration()) {
      return true;
    }
    if (!Comparing.equal(myDocstringFormatComboBox.getSelectedItem(), myDocumentationSettings.myDocStringFormat)) {
      return true;
    }
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
    myModel.apply();
    myDocumentationSettings.myDocStringFormat = (String) myDocstringFormatComboBox.getSelectedItem();
  }

  @Override
  public void reset() {
    myTestRunnerComboBox.setSelectedItem(myModel.getProjectConfiguration());
    myTestRunnerComboBox.repaint();
    myModel.reset();
    myDocstringFormatComboBox.setSelectedItem(myDocumentationSettings.myDocStringFormat);
  }

  @Override
  public void disposeUIResources() {
  }
}
