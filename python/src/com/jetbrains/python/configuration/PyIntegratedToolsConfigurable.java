package com.jetbrains.python.configuration;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.facet.impl.ui.FacetErrorPanel;
import com.intellij.facet.ui.FacetConfigurationQuickFix;
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
import com.jetbrains.python.packaging.*;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.testing.PythonTestConfigurationsModel;
import com.jetbrains.python.testing.TestRunnerService;
import com.jetbrains.python.testing.VFSTestFrameworkListener;
import com.jetbrains.rest.ReSTService;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;

/**
 * User: catherine
 */
public class PyIntegratedToolsConfigurable implements SearchableConfigurable, NonDefaultProjectConfigurable {
  private JPanel myMainPanel;
  private JComboBox myTestRunnerComboBox;
  private JComboBox myDocstringFormatComboBox;
  private PythonTestConfigurationsModel myModel;
  @NotNull private final Module myModule;
  @NotNull private final Project myProject;
  private final PyDocumentationSettings myDocumentationSettings;
  private TextFieldWithBrowseButton myWorkDir;
  private JCheckBox txtIsRst;
  private JPanel myErrorPanel;
  private TextFieldWithBrowseButton myRequirementsPathField;

  public PyIntegratedToolsConfigurable(@NotNull Module module) {
    myModule = module;
    myProject = myModule.getProject();
    myDocumentationSettings = PyDocumentationSettings.getInstance(myProject);
    myDocstringFormatComboBox.setModel(new CollectionComboBoxModel(DocStringFormat.ALL, myDocumentationSettings.myDocStringFormat));

    final FileChooserDescriptor fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    myWorkDir.addBrowseFolderListener("Please choose working directory:", null, myProject, fileChooserDescriptor);
    ReSTService service = ReSTService.getInstance(myProject);
    myWorkDir.setText(service.getWorkdir());
    txtIsRst.setSelected(service.txtIsRst());
    myRequirementsPathField.addBrowseFolderListener("Choose path to the package requirements file:", null, myProject,
                                                    FileChooserDescriptorFactory.createSingleLocalFileDescriptor());
    myRequirementsPathField.setText(getRequirementsPath());
  }

  @NotNull
  private String getRequirementsPath() {
    final String path = PyPackageRequirementsSettings.getInstance(myModule).getRequirementsPath();
    final String text;
    if (path.equals(PyPackageRequirementsSettings.DEFAULT_REQUIREMENTS_PATH) && PyPackageUtil.findRequirementsTxt(myModule) == null) {
      return "";
    }
    else {
      return path;
    }
  }

  private void initErrorValidation() {
    final FacetErrorPanel facetErrorPanel = new FacetErrorPanel();
    myErrorPanel.add(facetErrorPanel.getComponent(), BorderLayout.CENTER);

    facetErrorPanel.getValidatorsManager().registerValidator(new FacetEditorValidator() {
      @Override
      public ValidationResult check() {
        Module[] modules = ModuleManager.getInstance(myProject).getModules();
        if (modules.length == 0) return ValidationResult.OK;
        final Sdk sdk = PythonSdkType.findPythonSdk(modules[0]);
        if (sdk != null) {
          final Object selectedItem = myTestRunnerComboBox.getSelectedItem();
          if (PythonTestConfigurationsModel.PY_TEST_NAME.equals(selectedItem)) {
            if (!VFSTestFrameworkListener.getInstance().isPyTestInstalled(sdk.getHomePath())) {
              return new ValidationResult(PyBundle.message("runcfg.testing.no.test.framework", "py.test"),
                                          createQuickFix(sdk, facetErrorPanel, "pytest"));
            }
          }
          else if (PythonTestConfigurationsModel.PYTHONS_NOSETEST_NAME.equals(selectedItem)) {
            if (!VFSTestFrameworkListener.getInstance().isNoseTestInstalled(sdk.getHomePath())) {
              return new ValidationResult(PyBundle.message("runcfg.testing.no.test.framework", "nosetest"),
                                          createQuickFix(sdk, facetErrorPanel, "nose"));
            }
          }
          else if (PythonTestConfigurationsModel.PYTHONS_ATTEST_NAME.equals(selectedItem)) {
            if (!VFSTestFrameworkListener.getInstance().isAtTestInstalled(sdk.getHomePath())) {
              return new ValidationResult(PyBundle.message("runcfg.testing.no.test.framework", "attest"),
                                          createQuickFix(sdk, facetErrorPanel, "attest"));
            }
          }
        }
        return ValidationResult.OK;
      }
    }, myTestRunnerComboBox);

    facetErrorPanel.getValidatorsManager().validate();
  }

  private FacetConfigurationQuickFix createQuickFix(final Sdk sdk, final FacetErrorPanel facetErrorPanel, final String name) {
    return new FacetConfigurationQuickFix() {
      @Override
      public void run(JComponent place) {
        final PyPackageManager.UI ui = new PyPackageManager.UI(myProject, sdk, new PyPackageManager.UI.Listener() {
          @Override
          public void started() {}

          @Override
          public void finished(List<PyExternalProcessException> exceptions) {
            if (exceptions.isEmpty()) {
              VFSTestFrameworkListener.getInstance().testInstalled(true, sdk.getHomePath(), name);
              facetErrorPanel.getValidatorsManager().validate();
            }
          }
        });
        ui.install(Collections.singletonList(new PyRequirement(name)), Collections.<String>emptyList());
      }
    };
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
    initErrorValidation();
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
    if (!ReSTService.getInstance(myProject).getWorkdir().equals(myWorkDir.getText())) {
      return true;
    }
    if (!ReSTService.getInstance(myProject).txtIsRst() == txtIsRst.isSelected()) {
      return true;
    }
    if (!getRequirementsPath().equals(myRequirementsPathField.getText())) {
      return true;
    }
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
    myModel.apply();
    myDocumentationSettings.myDocStringFormat = (String) myDocstringFormatComboBox.getSelectedItem();
    ReSTService.getInstance(myProject).setWorkdir(myWorkDir.getText());
    ReSTService.getInstance(myProject).setTxtIsRst(txtIsRst.isSelected());
    PyPackageRequirementsSettings.getInstance(myModule).setRequirementsPath(myRequirementsPathField.getText());
    DaemonCodeAnalyzer.getInstance(myProject).restart();
  }

  @Override
  public void reset() {
    myTestRunnerComboBox.setSelectedItem(myModel.getProjectConfiguration());
    myTestRunnerComboBox.repaint();
    myModel.reset();
    myDocstringFormatComboBox.setSelectedItem(myDocumentationSettings.myDocStringFormat);
    myWorkDir.setText(ReSTService.getInstance(myProject).getWorkdir());
    txtIsRst.setSelected(ReSTService.getInstance(myProject).txtIsRst());
    myRequirementsPathField.setText(getRequirementsPath());
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

