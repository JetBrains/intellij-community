package com.jetbrains.python.testing.attest;

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
 * User: catherine
 */
public class PythonAtTestRunConfigurationForm implements PythonAtTestRunConfigurationParams {
  private JPanel myRootPanel;

  private final PythonTestRunConfigurationForm myTestRunConfigurationForm;


  public PythonAtTestRunConfigurationForm(final Project project, final PythonAtTestRunConfiguration configuration) {
    myRootPanel = new JPanel(new BorderLayout());
    myTestRunConfigurationForm = new PythonTestRunConfigurationForm(project, configuration);
    TitledBorder border = (TitledBorder)myTestRunConfigurationForm.getTestsPanel().getBorder();
    border.setTitle(PyBundle.message("runcfg.attest.display_name"));

    myTestRunConfigurationForm.addTestTypeListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        myTestRunConfigurationForm.setPatternVisible(myTestRunConfigurationForm.getTestType() == AbstractPythonTestRunConfiguration.TestType.TEST_FOLDER);
      }
    });
    myRootPanel.add(myTestRunConfigurationForm.getPanel(), BorderLayout.CENTER);
  }

  @Override
  public AbstractPythonTestRunConfigurationParams getTestRunConfigurationParams() {
    return myTestRunConfigurationForm;
  }

  public JComponent getPanel() {
    return myRootPanel;
  }
}


