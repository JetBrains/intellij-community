package com.jetbrains.python.run;

import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.RawCommandLineEditor;
import com.jetbrains.python.configuration.PythonSdkCellRenderer;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class PythonRunConfigurationEditor extends SettingsEditor<PythonRunConfiguration> {
  private JPanel myRootPanel;
  private TextFieldWithBrowseButton _scriptTextField;
  private RawCommandLineEditor _parametersTextField;
  private TextFieldWithBrowseButton _workingDirectoryTextField;
  private EnvironmentVariablesComponent _envsComponent;
  private JComboBox myInterpreterComboBox;

  protected void resetEditorFrom(PythonRunConfiguration s) {
    _scriptTextField.setText(s.SCRIPT_NAME);
    _parametersTextField.setText(s.PARAMETERS);
    _workingDirectoryTextField.setText(s.WORKING_DIRECTORY);
    FileChooserDescriptor chooserDescriptor = new FileChooserDescriptor(true, false, false, false, false, false) {
      public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
        return file.isDirectory() || Comparing.equal(file.getExtension(), "py");
      }
    };
    //chooserDescriptor.setRoot(s.getProject().getBaseDir());

    ComponentWithBrowseButton.BrowseFolderActionListener<JTextField> listener =
        new ComponentWithBrowseButton.BrowseFolderActionListener<JTextField>("Select Script", "", _scriptTextField, s.getProject(),
                                                                             chooserDescriptor,
                                                                             TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT) {

          protected void onFileChoosen(VirtualFile chosenFile) {
            super.onFileChoosen(chosenFile);
            _workingDirectoryTextField.setText(chosenFile.getParent().getPath());
          }
        };

    _scriptTextField.addActionListener(listener);

    _workingDirectoryTextField.addBrowseFolderListener("Select Working Directory", "", s.getProject(),
                                                       new FileChooserDescriptor(false, true, false, false, false, false));
    _envsComponent.setEnvs(s.getEnvs());
    _envsComponent.setPassParentEnvs(s.PASS_PARENT_ENVS);

    List<Sdk> sdkList = new ArrayList<Sdk>();
    sdkList.add(null);
    final List<Sdk> allSdks = PythonSdkType.getAllSdks();
    Sdk selection = null;
    for(Sdk sdk: allSdks) {
      if (FileUtil.pathsEqual(sdk.getHomePath(), s.SDK_HOME)) {
        selection = sdk;
        break;
      }
    }
    sdkList.addAll(allSdks);
    myInterpreterComboBox.setModel(new CollectionComboBoxModel(sdkList, selection));
    myInterpreterComboBox.setRenderer(new PythonSdkCellRenderer("<Project Default>"));
  }

  protected void applyEditorTo(PythonRunConfiguration s) throws ConfigurationException {
    s.SCRIPT_NAME = _scriptTextField.getText();
    s.PARAMETERS = _parametersTextField.getText();
    s.WORKING_DIRECTORY = _workingDirectoryTextField.getText();
    s.setEnvs(_envsComponent.getEnvs());
    s.PASS_PARENT_ENVS = _envsComponent.isPassParentEnvs();

    Sdk selectedSdk = (Sdk) myInterpreterComboBox.getSelectedItem();
    s.SDK_HOME = selectedSdk == null ? null : selectedSdk.getHomePath();
  }

  @NotNull
  protected JComponent createEditor() {
    return myRootPanel;
  }

  protected void disposeEditor() {

  }
}
