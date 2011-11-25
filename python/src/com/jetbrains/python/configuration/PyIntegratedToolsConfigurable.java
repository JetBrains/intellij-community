package com.jetbrains.python.configuration;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.facet.impl.ui.FacetErrorPanel;
import com.intellij.facet.ui.FacetEditorValidator;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.NonDefaultProjectConfigurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.CollectionComboBoxModel;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.documentation.DocStringFormat;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.testing.PythonTestConfigurationsModel;
import com.jetbrains.python.testing.TestRunnerService;
import com.jetbrains.python.testing.VFSTestFrameworkListener;
import com.jetbrains.rest.ReSTService;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * User: catherine
 */
public class PyIntegratedToolsConfigurable implements SearchableConfigurable, NonDefaultProjectConfigurable {
  private JPanel myMainPanel;
  private JComboBox myTestRunnerComboBox;
  private JComboBox myDocstringFormatComboBox;
  private PythonTestConfigurationsModel myModel;
  private Project myProject;
  private final PyDocumentationSettings myDocumentationSettings;
  private TextFieldWithBrowseButton myWorkDir;
  private JCheckBox txtIsRst;
  private JPanel myErrorPanel;

  public PyIntegratedToolsConfigurable(Project project) {
    myProject = project;
    myDocumentationSettings = PyDocumentationSettings.getInstance(project);
    myDocstringFormatComboBox.setModel(new CollectionComboBoxModel(DocStringFormat.ALL, myDocumentationSettings.myDocStringFormat));

    final FileChooserDescriptor fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    myWorkDir.addBrowseFolderListener("Please choose working directory:", null, project, fileChooserDescriptor);
    ReSTService service = ReSTService.getInstance(myProject);
    myWorkDir.setText(service.getWorkdir());
    txtIsRst.setSelected(service.txtIsRst());
    initErrorValidation();
  }

  private void initErrorValidation() {
    FacetErrorPanel facetErrorPanel = new FacetErrorPanel();
    myErrorPanel.add(facetErrorPanel.getComponent(), BorderLayout.CENTER);

    facetErrorPanel.getValidatorsManager().registerValidator(new FacetEditorValidator() {
      @Override
      public ValidationResult check() {
        Module[] modules = ModuleManager.getInstance(myProject).getModules();
        if (modules.length == 0) return ValidationResult.OK;
        final Sdk sdk = PythonSdkType.findPythonSdk(modules[0]);
        if (sdk != null) {
          if (myTestRunnerComboBox.getSelectedItem() == PythonTestConfigurationsModel.PY_TEST_NAME) {
            if (!VFSTestFrameworkListener.getInstance().isPyTestInstalled(sdk.getHomePath()))
              return new ValidationResult(PyBundle.message("runcfg.testing.no.test.framework", "py.test"));
          }
          else if (myTestRunnerComboBox.getSelectedItem() == PythonTestConfigurationsModel.PYTHONS_NOSETEST_NAME) {
            if (!VFSTestFrameworkListener.getInstance().isNoseTestInstalled(sdk.getHomePath()))
              return new ValidationResult(PyBundle.message("runcfg.testing.no.test.framework", "nosetest"));
          }
          else if (myTestRunnerComboBox.getSelectedItem() == PythonTestConfigurationsModel.PYTHONS_ATTEST_NAME) {
            if (!VFSTestFrameworkListener.getInstance().isAtTestInstalled(sdk.getHomePath()))
              return new ValidationResult(PyBundle.message("runcfg.testing.no.test.framework", "attest"));
          }
        }
        return ValidationResult.OK;
      }
    }, myTestRunnerComboBox);

    facetErrorPanel.getValidatorsManager().validate();
  }


  @Nls
  @Override
  public String getDisplayName() {
    return "Python Integrated Tools";
  }

  @Override
  public Icon getIcon() {
    return null;
  }

  @Override
  public String getHelpTopic() {
    return "test_runner_configuration";
  }

  @Override
  public JComponent createComponent() {
    List<String> configurations = TestRunnerService.getInstance(myProject).getConfigurations();
    myModel = new PythonTestConfigurationsModel(configurations, TestRunnerService.getInstance(myProject).getProjectConfiguration(),
                                                myProject);

    updateConfigurations();
    return myMainPanel;
  }

  private void updateConfigurations() {
    myTestRunnerComboBox.setModel(myModel);
  }

  @Override
  public boolean isModified() {
    if (myTestRunnerComboBox.getSelectedItem() != myModel.getProjectConfiguration()) {
      return true;
    }
    if (!Comparing.equal(myDocstringFormatComboBox.getSelectedItem(), myDocumentationSettings.myDocStringFormat)) {
      DaemonCodeAnalyzer.getInstance(myProject).restart();
      return true;
    }
    if (!ReSTService.getInstance(myProject).getWorkdir().equals(myWorkDir.getText()))
      return true;
    if (!ReSTService.getInstance(myProject).txtIsRst() == txtIsRst.isSelected())
      return true;
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
    myModel.apply();
    myDocumentationSettings.myDocStringFormat = (String) myDocstringFormatComboBox.getSelectedItem();
    ReSTService.getInstance(myProject).setWorkdir(myWorkDir.getText());
    ReSTService.getInstance(myProject).setTxtIsRst(txtIsRst.isSelected());
  }

  @Override
  public void reset() {
    myTestRunnerComboBox.setSelectedItem(myModel.getProjectConfiguration());
    myTestRunnerComboBox.repaint();
    myModel.reset();
    myDocstringFormatComboBox.setSelectedItem(myDocumentationSettings.myDocStringFormat);
    myWorkDir.setText(ReSTService.getInstance(myProject).getWorkdir());
    txtIsRst.setSelected(ReSTService.getInstance(myProject).txtIsRst());
  }

  @Override
  public void disposeUIResources() {
  }

  @NotNull
  @Override
  public String getId() {
    return "PyIntegratedToolsConfigurable";
  }

  @Override
  public Runnable enableSearch(String option) {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }
}

