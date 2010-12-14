package com.jetbrains.python.testing;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.NonDefaultProjectConfigurable;
import com.jetbrains.python.testing.nosetest.PythonNoseTestConfigurationProducer;
import com.jetbrains.python.testing.pytest.PyTestConfigurationProducer;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * User: catherine
 */
public class TestRunnerConfigurable implements Configurable, NonDefaultProjectConfigurable {
  private JPanel myMainPanel;
  private JComboBox myComboBox;
  private PythonTestConfigurationsModel myModel;

  @Nls
  @Override
  public String getDisplayName() {
    return "Test Runner";
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
    myModel = PythonTestConfigurationsModel.getInstance();
    updateConfigurations();
    return myMainPanel;
  }

  private void updateConfigurations() {
    myComboBox.setModel(myModel);
    myComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        JComboBox cb = (JComboBox)actionEvent.getSource();
        String selectedItem = (String)cb.getSelectedItem();
        if (selectedItem.equals(PythonTestConfigurationsModel.PYTHONS_UNITTEST_NAME)) {
          PythonUnitTestConfigurationProducer.getInstance(PythonUnitTestConfigurationProducer.class).setActive(true);
          PythonNoseTestConfigurationProducer.getInstance(PythonNoseTestConfigurationProducer.class).setActive(false);
          PyTestConfigurationProducer.getInstance(PyTestConfigurationProducer.class).setActive(false);
        }
        else if (selectedItem.equals(PythonTestConfigurationsModel.PYTHONS_NOSETEST_NAME)) {
          PythonNoseTestConfigurationProducer.getInstance(PythonNoseTestConfigurationProducer.class).setActive(true);
          PythonUnitTestConfigurationProducer.getInstance(PythonUnitTestConfigurationProducer.class).setActive(false);
          PyTestConfigurationProducer.getInstance(PyTestConfigurationProducer.class).setActive(false);
        }
        else if (selectedItem.equals(PythonTestConfigurationsModel.PY_TEST_NAME)) {
          PyTestConfigurationProducer.getInstance(PyTestConfigurationProducer.class).setActive(true);
          PythonNoseTestConfigurationProducer.getInstance(PythonNoseTestConfigurationProducer.class).setActive(false);
          PythonUnitTestConfigurationProducer.getInstance(PythonUnitTestConfigurationProducer.class).setActive(false);
        }
      }
    });
  }

  @Override
  public boolean isModified() {
    if (myComboBox.getSelectedItem() != myModel.getProjectConfiguration()) {
      return true;
    }
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
    myModel.apply();
  }

  @Override
  public void reset() {
    myComboBox.setSelectedItem(myModel.getProjectConfiguration());
    myComboBox.repaint();
    myModel.reset();
  }

  @Override
  public void disposeUIResources() {
    //To change body of implemented methods use File | Settings | File Templates.
  }
}
