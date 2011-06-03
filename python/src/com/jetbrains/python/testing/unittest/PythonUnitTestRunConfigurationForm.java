package com.jetbrains.python.testing.unittest;

import com.intellij.openapi.project.Project;
import com.jetbrains.python.testing.AbstractPythonTestRunConfigurationParams;
import com.jetbrains.python.testing.PythonTestRunConfigurationForm;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
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

    final JRadioButton functionRB = myTestRunConfigurationForm.getFunctionRB();
    functionRB.setEnabled(!myIsPureUnittest.isSelected());

    myIsPureUnittest.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        functionRB.setEnabled(!myIsPureUnittest.isSelected());
      }
    });

    functionRB.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        myIsPureUnittest.setEnabled(!functionRB.isSelected());
      }
    });

    myIsPureUnittest.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        configuration.setPureUnittest(myIsPureUnittest.isSelected());
      }
    });
    myTestRunConfigurationForm.getAdditionalPanel().add(myIsPureUnittest);
    myRootPanel.add(myTestRunConfigurationForm.getPanel(), BorderLayout.CENTER);
  }
  public String getPattern() {
    return myTestRunConfigurationForm.getPattern();
  }

  public void setPattern(String pattern) {
    myTestRunConfigurationForm.setPattern(pattern);
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


