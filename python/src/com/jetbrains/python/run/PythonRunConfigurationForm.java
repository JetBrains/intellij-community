package com.jetbrains.python.run;

import com.intellij.ide.ui.ListCellRendererWrapper;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.components.JBLabel;
import com.jetbrains.python.debugger.remote.PyRemoteDebugConfiguration;
import com.jetbrains.python.remote.PythonRemoteSdkAdditionalData;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

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
  private JComboBox myRemoteConfigurationCombo;
  private JPanel myRemoteDebugConfigurationPanel;
  private final AbstractPyCommonOptionsForm myCommonOptionsForm;
  private JComponent anchor;
  private boolean myRemoteInterpreterMode;
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

    myCommonOptionsForm.addInterpreterComboBoxActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        updateRemoteInterpreterMode();
      }
    }
    );

    updateRemoteInterpreterMode();
  }

  private void updateRemoteInterpreterMode() {
    setRemoteInterpreterMode(isRemoteSdkSelected());
    Object selection = myRemoteConfigurationCombo.getSelectedItem();
    myRemoteConfigurationCombo
      .setModel(new CollectionComboBoxModel(PyRemoteDebugConfiguration.listAllRemoteDebugConfigurations(myProject), selection));
    myRemoteConfigurationCombo
      .setRenderer(new ListCellRendererWrapper<PyRemoteDebugConfiguration>(myRemoteConfigurationCombo.getRenderer()) {
        @Override
        public void customize(JList list, PyRemoteDebugConfiguration value, int index, boolean selected, boolean hasFocus) {
          if (value != null) {
            setText(value.getName());
            setIcon(value.getIcon());
          }
          else {
            setText("<Select Python Remote Debug Configuration>");
            setIcon(null);
          }
        }
      });
  }

  private boolean isRemoteSdkSelected() {
    String sdkHome = myCommonOptionsForm.getSdkHome();
    if (StringUtil.isEmptyOrSpaces(sdkHome)) {
      final Sdk projectJdk = PythonSdkType.findPythonSdk(myCommonOptionsForm.getModule());
      if (projectJdk != null) {
        sdkHome = projectJdk.getHomePath();
      }
    }

    return isRemoteSdkSelected(sdkHome);
  }

  public static boolean isRemoteSdkSelected(String sdkHome) {
    Sdk sdk = PythonSdkType.findSdkByPath(sdkHome);
    return sdk != null && sdk.getSdkAdditionalData() instanceof PythonRemoteSdkAdditionalData;
  }

  private void setRemoteInterpreterMode(boolean remoteInterpreterMode) {
    myRemoteInterpreterMode = remoteInterpreterMode;
    myRemoteDebugConfigurationPanel.setVisible(remoteInterpreterMode);
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

  @Nullable
  @Override
  public String getRemoteDebugConfiguration() {
    Object selection = myRemoteConfigurationCombo.getSelectedItem();
    if (selection instanceof PyRemoteDebugConfiguration) {
      return ((PyRemoteDebugConfiguration)selection).getName();
    }
    return null;
  }

  @Override
  public void setRemoteDebugConfiguration(String name) {
    PyRemoteDebugConfiguration conf = PyRemoteDebugConfiguration.findByName(myProject, name);
    myRemoteConfigurationCombo.setSelectedItem(conf);
  }

  @Override
  public void setAnchor(JComponent anchor) {
    this.anchor = anchor;
    myScriptParametersLabel.setAnchor(anchor);
    myCommonOptionsForm.setAnchor(anchor);
  }
}
