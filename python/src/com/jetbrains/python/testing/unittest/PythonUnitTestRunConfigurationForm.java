package com.jetbrains.python.testing.unittest;

import com.intellij.openapi.project.Project;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.testing.AbstractPythonTestRunConfiguration;
import com.jetbrains.python.testing.AbstractPythonTestRunConfigurationParams;
import com.jetbrains.python.testing.PythonTestRunConfigurationForm;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Leonid Shalupov
 */
public class PythonUnitTestRunConfigurationForm implements PythonUnitTestRunConfigurationParams {
  private JPanel myRootPanel;
  private JCheckBox myIsPureUnittest;

  private PythonTestRunConfigurationForm myTestRunConfigurationForm;


  public PythonUnitTestRunConfigurationForm(final Project project, final PythonUnitTestRunConfiguration configuration) {
    myRootPanel = new JPanel(new BorderLayout());
    myTestRunConfigurationForm = new PythonTestRunConfigurationForm(project, configuration);
    myIsPureUnittest = new JCheckBox("Inspect only subclasses of unittest.TestCase");
    myIsPureUnittest.setSelected(configuration.isPureUnittest());

    final ActionListener testTypeListener = new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        myIsPureUnittest.setVisible(myTestRunConfigurationForm.getTestType() != AbstractPythonTestRunConfiguration.TestType.TEST_FUNCTION);
      }
    };
    myTestRunConfigurationForm.addTestTypeListener(testTypeListener);

    myIsPureUnittest.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        configuration.setPureUnittest(myIsPureUnittest.isSelected());
      }
    });
    myTestRunConfigurationForm.getAdditionalPanel().add(myIsPureUnittest);
    TitledBorder border = (TitledBorder)myTestRunConfigurationForm.getTestsPanel().getBorder();
    border.setTitle(PyBundle.message("runcfg.unittest.display_name"));

    myRootPanel.add(myTestRunConfigurationForm.getPanel(), BorderLayout.CENTER);
  }

  @Override
  public AbstractPythonTestRunConfigurationParams getTestRunConfigurationParams() {
    return myTestRunConfigurationForm;
  }

  @Override
  public boolean isPureUnittest() {
    return myIsPureUnittest.isSelected();
  }

  @Override
  public void setPureUnittest(boolean isPureUnittest) {
    myIsPureUnittest.setSelected(isPureUnittest);
  }

  public JComponent getPanel() {
    return myRootPanel;
  }
}


