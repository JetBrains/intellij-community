/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.testing.unittestLegacy;

import com.intellij.openapi.project.Project;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.testing.AbstractPythonLegacyTestRunConfiguration;
import com.jetbrains.python.testing.AbstractPythonTestRunConfigurationParams;
import com.jetbrains.python.testing.PythonTestLegacyRunConfigurationForm;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Leonid Shalupov
 */
public class PythonUnitTestRunConfigurationForm implements PythonUnitTestRunConfigurationParams {
  private final JPanel myRootPanel;
  private final JCheckBox myIsPureUnittest;

  private final PythonTestLegacyRunConfigurationForm myTestRunConfigurationForm;


  public PythonUnitTestRunConfigurationForm(final Project project, final PythonUnitTestRunConfiguration configuration) {
    myRootPanel = new JPanel(new BorderLayout());
    myTestRunConfigurationForm = new PythonTestLegacyRunConfigurationForm(project, configuration);
    myIsPureUnittest = new JCheckBox("Inspect only subclasses of unittest.TestCase");
    myIsPureUnittest.setSelected(configuration.isPureUnittest());

    final ActionListener testTypeListener = new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        myIsPureUnittest.setVisible(myTestRunConfigurationForm.getTestType() != AbstractPythonLegacyTestRunConfiguration.TestType.TEST_FUNCTION);
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
    myTestRunConfigurationForm.setParamsVisible();
    myTestRunConfigurationForm.getParamCheckBox().setSelected(configuration.useParam());

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

  public String getParams() {
    return myTestRunConfigurationForm.getParams();
  }

  public void setParams(String params) {
    myTestRunConfigurationForm.setParams(params);
  }

  @Override
  public boolean useParam() {
    return myTestRunConfigurationForm.getParamCheckBox().isSelected();
  }

  @Override
  public void useParam(boolean useParam) {
    myTestRunConfigurationForm.getParamCheckBox().setSelected(useParam);
  }

  public JComponent getPanel() {
    return myRootPanel;
  }
}


