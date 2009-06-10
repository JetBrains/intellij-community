package com.jetbrains.python.testing;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.run.AbstractPythonRunConfigurationParams;
import com.jetbrains.python.run.PyCommonOptionsForm;
import com.jetbrains.python.run.PythonRunConfigurationFormUtil;
import static com.jetbrains.python.testing.PythonUnitTestRunConfiguration.TestType;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Leonid Shalupov
 */
public class PythonUnitTestRunConfigurationForm implements PythonUnitTestRunConfigurationParams {
  private JPanel myRootPanel;
  private LabeledComponent myTestClassComponent;
  private LabeledComponent myTestMethodComponent;
  private LabeledComponent myTestFolderComponent;
  private LabeledComponent myTestScriptComponent;
  private JRadioButton myAllInFolderRB;
  private JRadioButton myTestScriptRB;
  private JRadioButton myTestClassRB;
  private JRadioButton myTestMethodRB;
  private JPanel myCommonOptionsPlaceholder;

  private TextFieldWithBrowseButton myTestFolderTextField;
  private TextFieldWithBrowseButton myTestScriptTextField;
  private JTextField myTestMethodTextField;
  private JTextField myTestClassTextField;

  private final Project myProject;
  private PyCommonOptionsForm myCommonOptionsForm;

  public PythonUnitTestRunConfigurationForm(final Project project, final PythonUnitTestRunConfiguration configuration) {
    myProject = project;

    myCommonOptionsForm = new PyCommonOptionsForm(configuration);
    myCommonOptionsPlaceholder.add(myCommonOptionsForm.getMainPanel(), BorderLayout.CENTER);
    
    initComponents();
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

    myAllInFolderRB.addActionListener(testTypeListener);
    myTestScriptRB.addActionListener(testTypeListener);
    myTestClassRB.addActionListener(testTypeListener);
    myTestMethodRB.addActionListener(testTypeListener);
  }

  public String getClassName() {
    return myTestClassTextField.getText().trim();
  }

  public void setClassName(String className) {
    myTestClassTextField.setText(className);
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
    else {
      return TestType.TEST_METHOD;
    }
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

    myTestFolderComponent.setEnabled(testType == TestType.TEST_FOLDER);
    myTestScriptComponent.setEnabled(testType != TestType.TEST_FOLDER);
    myTestClassComponent.setEnabled(testType == TestType.TEST_CLASS || testType == TestType.TEST_METHOD);
    myTestMethodComponent.setEnabled(testType == TestType.TEST_METHOD);
  }

  public JComponent getPanel() {
    return myRootPanel;
  }

  private static LabeledComponent createTestFolderComponent(final Ref<TextFieldWithBrowseButton> testsFolderTextFieldWrapper) {
    final TextFieldWithBrowseButton testsFolderTextField = new TextFieldWithBrowseButton();
    testsFolderTextFieldWrapper.set(testsFolderTextField);

    LabeledComponent<TextFieldWithBrowseButton> myComponent = new LabeledComponent<TextFieldWithBrowseButton>();
    myComponent.setComponent(testsFolderTextField);
    myComponent.setText(PyBundle.message("runcfg.unittest.dlg.folder_path"));

    return myComponent;
  }

  public static LabeledComponent<TextFieldWithBrowseButton> createScriptPathComponent(final Ref<TextFieldWithBrowseButton> testScriptTextFieldWrapper,
                                                                                      final String text) {
    final TextFieldWithBrowseButton testScriptTextField = new TextFieldWithBrowseButton();
    testScriptTextFieldWrapper.set(testScriptTextField);

    LabeledComponent<TextFieldWithBrowseButton> myComponent = new LabeledComponent<TextFieldWithBrowseButton>();
    myComponent.setComponent(testScriptTextField);
    myComponent.setText(text);

    return myComponent;
  }

  private LabeledComponent createTestClassComponent() {
    myTestClassTextField = new JTextField();

    LabeledComponent<JTextField> myComponent = new LabeledComponent<JTextField>();
    myComponent.setComponent(myTestClassTextField);
    myComponent.setText(PyBundle.message("runcfg.unittest.dlg.class_label"));

    return myComponent;
  }

  private LabeledComponent createTestMethodComponent() {
    myTestMethodTextField = new JTextField();

    LabeledComponent<JTextField> myComponent = new LabeledComponent<JTextField>();

    myComponent.setComponent(myTestMethodTextField);
    myComponent.setText(PyBundle.message("runcfg.unittest.dlg.method_label"));

    return myComponent;
  }

  private void createUIComponents() {
    myTestClassComponent = createTestClassComponent();
    myTestMethodComponent = createTestMethodComponent();

    final Ref<TextFieldWithBrowseButton> testsFolderTextFieldWrapper = new Ref<TextFieldWithBrowseButton>();
    myTestFolderComponent = createTestFolderComponent(testsFolderTextFieldWrapper);
    myTestFolderTextField = testsFolderTextFieldWrapper.get();
    String title = PyBundle.message("runcfg.unittest.dlg.select.folder.path");
    PythonRunConfigurationFormUtil.addFolderChooser(title, myTestFolderTextField, myProject);

    final Ref<TextFieldWithBrowseButton> testScriptTextFieldWrapper = new Ref<TextFieldWithBrowseButton>();
    myTestScriptComponent = createScriptPathComponent(testScriptTextFieldWrapper, PyBundle.message("runcfg.unittest.dlg.folder_path"));
    myTestScriptTextField = testScriptTextFieldWrapper.get();
    title = PyBundle.message("runcfg.unittest.dlg.select.script.path");
    PythonRunConfigurationFormUtil.addFileChooser(title, myTestScriptTextField, myProject);
  }
}


