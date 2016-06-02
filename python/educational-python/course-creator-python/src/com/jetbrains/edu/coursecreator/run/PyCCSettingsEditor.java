package com.jetbrains.edu.coursecreator.run;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.run.AbstractPyCommonOptionsForm;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.run.PyCommonOptionsFormData;
import com.jetbrains.python.run.PyCommonOptionsFormFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class PyCCSettingsEditor extends SettingsEditor<PyCCRunTestConfiguration> {
  private AbstractPyCommonOptionsForm myForm;
  private Project myProject;
  private JTextField myPathToTestFileField;
  private JPanel myPanel;

  public PyCCSettingsEditor(Project project) {
    myProject = project;
  }

  @Override
  protected void resetEditorFrom(PyCCRunTestConfiguration s) {
    AbstractPythonRunConfiguration.copyParams(s, myForm);
    myPathToTestFileField.setText(s.getPathToTest());
  }

  @Override
  protected void applyEditorTo(PyCCRunTestConfiguration s) throws ConfigurationException {
    AbstractPythonRunConfiguration.copyParams(myForm, s);
    s.setPathToTest(myPathToTestFileField.getText());
  }

  @NotNull
  @Override
  protected JComponent createEditor() {
    final JPanel mainPanel = new JPanel(new BorderLayout());
    mainPanel.add(myPanel, BorderLayout.NORTH);
    myForm = createEnvPanel();
    mainPanel.add(myForm.getMainPanel(), BorderLayout.SOUTH);
    return mainPanel;
  }


  @NotNull
  private AbstractPyCommonOptionsForm createEnvPanel() {
    return PyCommonOptionsFormFactory.getInstance().createForm(new PyCommonOptionsFormData() {
      @Override
      public Project getProject() {
        return myProject;
      }

      @Override
      public List<Module> getValidModules() {
        return AbstractPythonRunConfiguration.getValidModules(myProject);
      }

      @Override
      public boolean showConfigureInterpretersLink() {
        return false;
      }
    });
  }
}
