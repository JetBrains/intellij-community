package com.jetbrains.python.run;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.components.JBLabel;

import javax.swing.*;
import java.awt.*;

/**
 * @author yole
 */
public class PythonRunConfigurationForm implements PythonRunConfigurationParams, PanelWithAnchor {
  private JPanel myRootPanel;
  private TextFieldWithBrowseButton myScriptTextField;
  private RawCommandLineEditor myScriptParametersTextField;
  private JPanel myCommonOptionsPlaceholder;
  private JBLabel myScriptParametersLabel;
  private JCheckBox myAttachDebuggerToSubprocess;
  private final AbstractPyCommonOptionsForm myCommonOptionsForm;
  private JComponent anchor;
  private final Project myProject;

  public PythonRunConfigurationForm(PythonRunConfiguration configuration) {
    myCommonOptionsForm = PyCommonOptionsFormFactory.getInstance().createForm(configuration.getCommonOptionsFormData());
    myCommonOptionsPlaceholder.add(myCommonOptionsForm.getMainPanel(), BorderLayout.CENTER);

    myProject = configuration.getProject();

    FileChooserDescriptor chooserDescriptor = new FileChooserDescriptor(true, false, false, false, false, false) {
      public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
        return file.isDirectory() || Comparing.equal(file.getExtension(), "py");
      }
    };
    //chooserDescriptor.setRoot(s.getProject().getBaseDir());

    ComponentWithBrowseButton.BrowseFolderActionListener<JTextField> listener =
      new ComponentWithBrowseButton.BrowseFolderActionListener<JTextField>("Select Script", "", myScriptTextField, myProject,
                                                                           chooserDescriptor, TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT) {

        protected void onFileChoosen(VirtualFile chosenFile) {
          super.onFileChoosen(chosenFile);
          myCommonOptionsForm.setWorkingDirectory(chosenFile.getParent().getPath());
        }
      };

    myScriptTextField.addActionListener(listener);

    setAnchor(myCommonOptionsForm.getAnchor());
  }

  public JComponent getPanel() {
    return myRootPanel;
  }

  public AbstractPythonRunConfigurationParams getBaseParams() {
    return myCommonOptionsForm;
  }

  public String getScriptName() {
    return FileUtil.toSystemIndependentName(myScriptTextField.getText().trim());
  }

  public void setScriptName(String scriptName) {
    myScriptTextField.setText(scriptName == null ? "" : FileUtil.toSystemDependentName(scriptName));
  }

  public String getScriptParameters() {
    return myScriptParametersTextField.getText().trim();
  }

  public void setScriptParameters(String scriptParameters) {
    myScriptParametersTextField.setText(scriptParameters);
  }

  @Override
  public JComponent getAnchor() {
    return anchor;
  }

  public boolean isMultiprocessMode() {
    return myAttachDebuggerToSubprocess.isSelected();
  }

  public void setMultiprocessMode(boolean multiprocess) {
    myAttachDebuggerToSubprocess.setSelected(multiprocess);
  }

  @Override
  public void setAnchor(JComponent anchor) {
    this.anchor = anchor;
    myScriptParametersLabel.setAnchor(anchor);
    myCommonOptionsForm.setAnchor(anchor);
  }
}
