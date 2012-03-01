package com.jetbrains.python.run;

import com.intellij.ide.DataManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.newEditor.OptionsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.components.JBLabel;
import com.jetbrains.python.debugger.remote.PyPathMappingSettings;
import com.jetbrains.python.debugger.remote.ui.PyMappingSettingsDialog;
import com.jetbrains.python.remote.PythonRemoteSdkAdditionalData;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

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
  private JPanel myMappingsConfigurationPanel;
  private final AbstractPyCommonOptionsForm myCommonOptionsForm;
  private JComponent anchor;
  private boolean myRemoteInterpreterMode;
  private final Project myProject;
  private PyPathMappingSettings myMappingSettings;
  private TextFieldWithBrowseButton myMappingsTextField;
  private JLabel myMappingsLabel;

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

    createConfigureMappingsLink();

    updateRemoteInterpreterMode();
  }

  private void createConfigureMappingsLink() {
    myMappingsTextField.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        showConfigureMappingsDialog();
      }
    });
    myMappingsTextField.setEditable(false);
  }

  private void updateRemoteInterpreterMode() {
    setRemoteInterpreterMode(isRemoteSdkSelected());
    if (myMappingSettings == null) {
      myMappingSettings = new PyPathMappingSettings();
    }
  }

  private void showConfigureMappingsDialog() {
    PyMappingSettingsDialog dialog = new PyMappingSettingsDialog(myProject, myMappingSettings);
    dialog.show();
    if (dialog.isOK()) {
      setMappingSettings(dialog.getMappingSettings());
    }
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
    myMappingsConfigurationPanel.setVisible(remoteInterpreterMode);
    myMappingsTextField.setVisible(remoteInterpreterMode);
    myMappingsLabel.setVisible(remoteInterpreterMode);
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
  public PyPathMappingSettings getMappingSettings() {
    return myMappingSettings;
  }

  public void setMappingSettings(PyPathMappingSettings mappingSettings) {
    myMappingSettings = mappingSettings;
    if (myMappingsTextField != null) {
      StringBuilder sb = new StringBuilder();
      if (mappingSettings != null) {
        for (PyPathMappingSettings.PyPathMapping mapping : mappingSettings.getPathMappings()) {
          sb.append(mapping.getLocalRoot()).append("=").append(mapping.getRemoteRoot()).append(";");
        }
      }
      myMappingsTextField.setText(sb.toString());
    }
  }

  @Override
  public void setAnchor(JComponent anchor) {
    this.anchor = anchor;
    myScriptParametersLabel.setAnchor(anchor);
    myCommonOptionsForm.setAnchor(anchor);
  }
}
