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
package com.jetbrains.python.testing;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.PanelWithAnchor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.run.AbstractPyCommonOptionsForm;
import com.jetbrains.python.run.AbstractPythonRunConfigurationParams;
import com.jetbrains.python.run.PyCommonOptionsFormFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;
import static com.jetbrains.python.testing.unittest.PythonUnitTestRunConfiguration.TestType;

/**
 * @author Leonid Shalupov
 */
public class PythonTestRunConfigurationForm implements AbstractPythonTestRunConfigurationParams, PanelWithAnchor {
  private JPanel myRootPanel;
  private LabeledComponent myTestClassComponent;
  private LabeledComponent myTestMethodComponent;
  private LabeledComponent myTestFolderComponent;
  private LabeledComponent myTestScriptComponent;
  private JRadioButton myAllInFolderRB;
  private JRadioButton myTestScriptRB;
  private JRadioButton myTestClassRB;
  private JRadioButton myTestMethodRB;
  private JRadioButton myTestFunctionRB;
  private JPanel myAdditionalPanel;
  private JPanel myCommonOptionsPlaceholder;
  private JPanel myTestsPanel;
  private JCheckBox myPatternCheckBox;

  private TextFieldWithBrowseButton myTestFolderTextField;
  private TextFieldWithBrowseButton myTestScriptTextField;
  private JTextField myTestMethodTextField;
  private JTextField myTestClassTextField;
  private JTextField myPatternTextField;
  private JTextField myParamTextField;
  private JCheckBox myParamCheckBox;

  private final Project myProject;
  private final AbstractPyCommonOptionsForm myCommonOptionsForm;
  private JComponent anchor;

  private boolean myPatternIsVisible = true;

  public PythonTestRunConfigurationForm(final Project project,
                                        final AbstractPythonTestRunConfiguration configuration) {
    myProject = project;
    myCommonOptionsForm = PyCommonOptionsFormFactory.getInstance().createForm(configuration.getCommonOptionsFormData());
    myCommonOptionsPlaceholder.add(myCommonOptionsForm.getMainPanel(), BorderLayout.CENTER);
    initComponents();

    setAnchor(myTestMethodComponent.getLabel());

    myTestFolderTextField.addBrowseFolderListener(PyBundle.message("runcfg.unittest.dlg.select.folder.path"), null, myProject,
                                                  FileChooserDescriptorFactory.createSingleFolderDescriptor());
    myTestScriptTextField.addBrowseFolderListener(PyBundle.message("runcfg.unittest.dlg.select.script.path"), null, myProject,
                                                  FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor());

    myPatternCheckBox.setSelected(configuration.usePattern());

    myParamTextField.setVisible(false);
    myParamCheckBox.setVisible(false);
  }

  public AbstractPythonRunConfigurationParams getBaseParams() {
    return myCommonOptionsForm;
  }

  private void initComponents() {

    final ActionListener testTypeListener = new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        setTestType(getTestType());
      }
    };
    addTestTypeListener(testTypeListener);

    myPatternCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        myPatternTextField.setEnabled(myPatternCheckBox.isSelected());
      }
    });

    myParamCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        myParamTextField.setEnabled(myParamCheckBox.isSelected());
      }
    });
  }

  public void addTestTypeListener(ActionListener testTypeListener) {
    myAllInFolderRB.addActionListener(testTypeListener);
    myTestScriptRB.addActionListener(testTypeListener);
    myTestClassRB.addActionListener(testTypeListener);
    myTestMethodRB.addActionListener(testTypeListener);
    myTestFunctionRB.addActionListener(testTypeListener);
  }

  public String getClassName() {
    return myTestClassTextField.getText().trim();
  }

  public void setClassName(String className) {
    myTestClassTextField.setText(className);
  }


  public String getPattern() {
    return myPatternTextField.getText().trim();
  }

  public void setPattern(String pattern) {
    myPatternTextField.setText(pattern);
  }

  @Override
  public boolean shouldAddContentRoots() {
    return myCommonOptionsForm.shouldAddContentRoots();
  }

  @Override
  public boolean shouldAddSourceRoots() {
    return myCommonOptionsForm.shouldAddSourceRoots();
  }

  @Override
  public void setAddContentRoots(boolean addContentRoots) {
    myCommonOptionsForm.setAddContentRoots(addContentRoots);
  }

  @Override
  public void setAddSourceRoots(boolean addSourceRoots) {
    myCommonOptionsForm.setAddSourceRoots(addSourceRoots);
  }

  public String getFolderName() {
    return toSystemIndependentName(myTestFolderTextField.getText().trim());
  }

  public void setFolderName(String folderName) {
    myTestFolderTextField.setText(FileUtil.toSystemDependentName(folderName));
  }

  public String getScriptName() {
    return toSystemIndependentName(myTestScriptTextField.getText().trim());
  }

  public void setScriptName(String scriptName) {
    myTestScriptTextField.setText(FileUtil.toSystemDependentName(scriptName));
  }

  public String getMethodName() {
    return myTestMethodTextField.getText().trim();
  }

  public void setMethodName(String methodName) {
    myTestMethodTextField.setText(methodName);
  }

  public TestType getTestType() {
    if (myAllInFolderRB.isSelected()) {
      return TestType.TEST_FOLDER;
    }
    else if (myTestScriptRB.isSelected()) {
      return TestType.TEST_SCRIPT;
    }
    else if (myTestClassRB.isSelected()) {
      return TestType.TEST_CLASS;
    }
    else if (myTestMethodRB.isSelected()) {
      return TestType.TEST_METHOD;
    }
    else {
      return TestType.TEST_FUNCTION;
    }
  }

  @Override
  public JComponent getAnchor() {
    return anchor;
  }

  @Override
  public void setAnchor(JComponent anchor) {
    this.anchor = anchor;
  }

  public void setPatternVisible(boolean b) {
    myPatternIsVisible = b;
    myPatternTextField.setVisible(b);
    myPatternCheckBox.setVisible(b);
  }

  private static void setSelectedIfNeeded(boolean condition, JRadioButton rb) {
    if (condition) {
      rb.setSelected(true);
    }
  }
  
  public void setTestType(TestType testType) {
    setSelectedIfNeeded(testType == TestType.TEST_FOLDER, myAllInFolderRB);
    setSelectedIfNeeded(testType == TestType.TEST_SCRIPT, myTestScriptRB);
    setSelectedIfNeeded(testType == TestType.TEST_CLASS, myTestClassRB);
    setSelectedIfNeeded(testType == TestType.TEST_METHOD, myTestMethodRB);
    setSelectedIfNeeded(testType == TestType.TEST_FUNCTION, myTestFunctionRB);


    myTestFolderComponent.setVisible(testType == TestType.TEST_FOLDER);
    myTestFolderTextField.setVisible(testType == TestType.TEST_FOLDER);
    myTestScriptComponent.setVisible(testType != TestType.TEST_FOLDER);
    myTestScriptTextField.setVisible(testType != TestType.TEST_FOLDER);
    myTestClassComponent.setVisible(testType == TestType.TEST_CLASS || testType == TestType.TEST_METHOD);
    myTestClassTextField.setVisible(testType == TestType.TEST_CLASS || testType == TestType.TEST_METHOD);
    myTestMethodComponent.setVisible(testType == TestType.TEST_METHOD || testType == TestType.TEST_FUNCTION);
    myTestMethodTextField.setVisible(testType == TestType.TEST_METHOD || testType == TestType.TEST_FUNCTION);
    myPatternTextField.setEnabled(myPatternCheckBox.isSelected());
    myParamTextField.setEnabled(myParamCheckBox.isSelected());
    myTestMethodComponent.getLabel().setText(testType == TestType.TEST_METHOD? PyBundle.message("runcfg.unittest.dlg.method_label")
                                                                             : PyBundle.message("runcfg.unittest.dlg.function_label"));
    if (myPatternIsVisible) {
      myPatternTextField.setVisible(getTestType() == AbstractPythonTestRunConfiguration.TestType.TEST_FOLDER);
      myPatternCheckBox.setVisible(getTestType() == AbstractPythonTestRunConfiguration.TestType.TEST_FOLDER);
    }
  }

  public JComponent getPanel() {
    return myRootPanel;
  }

  public JPanel getAdditionalPanel() {
    return myAdditionalPanel;
  }

  public JPanel getTestsPanel() {
    return myTestsPanel;
  }
  public JTextField getPatternComponent() {
    return myPatternTextField;
  }

  @Override
  public boolean usePattern() {
    return myPatternCheckBox.isSelected();
  }

  @Override
  public void usePattern(boolean usePattern) {
    myPatternCheckBox.setSelected(usePattern);
  }

  public String getParams() {
    return myParamTextField.getText().trim();
  }

  public JCheckBox getParamCheckBox() {
    return myParamCheckBox;
  }

  public void setParams(String params) {
    myParamTextField.setText(params);
  }

  public void setParamsVisible() {
    myParamTextField.setVisible(true);
    myParamCheckBox.setVisible(true);
  }
}


