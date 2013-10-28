/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.configuration;

import com.google.common.collect.Lists;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.facet.impl.ui.FacetErrorPanel;
import com.intellij.facet.ui.FacetConfigurationQuickFix;
import com.intellij.facet.ui.FacetEditorValidator;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.NonDefaultProjectConfigurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.FileContentUtil;
import com.intellij.util.FileContentUtilCore;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.ReSTService;
import com.jetbrains.python.documentation.DocStringFormat;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.packaging.*;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.testing.PythonTestConfigurationsModel;
import com.jetbrains.python.testing.TestRunnerService;
import com.jetbrains.python.testing.VFSTestFrameworkListener;
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
  private JCheckBox analyzeDoctest;
  private JPanel myDocStringsPanel;
  private JPanel myRestPanel;

  public PyIntegratedToolsConfigurable(@NotNull Module module) {
    myModule = module;
    myProject = myModule.getProject();
    myDocumentationSettings = PyDocumentationSettings.getInstance(myModule);
    myDocstringFormatComboBox.setModel(new CollectionComboBoxModel(DocStringFormat.ALL, myDocumentationSettings.myDocStringFormat));

    final FileChooserDescriptor fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    myWorkDir.addBrowseFolderListener("Please choose working directory:", null, myProject, fileChooserDescriptor);
    ReSTService service = ReSTService.getInstance(myModule);
    myWorkDir.setText(service.getWorkdir());
    txtIsRst.setSelected(service.txtIsRst());
    analyzeDoctest.setSelected(myDocumentationSettings.analyzeDoctest);
    myRequirementsPathField.addBrowseFolderListener("Choose path to the package requirements file:", null, myProject,
                                                    FileChooserDescriptorFactory.createSingleLocalFileDescriptor());
    myRequirementsPathField.setText(getRequirementsPath());

    myDocStringsPanel.setBorder(IdeBorderFactory.createTitledBorder("Docstrings"));
    myRestPanel.setBorder(IdeBorderFactory.createTitledBorder("reStructuredText"));
  }

  @NotNull
  private String getRequirementsPath() {
    final String path = PyPackageRequirementsSettings.getInstance(myModule).getRequirementsPath();
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
            if (!VFSTestFrameworkListener.getInstance().isPyTestInstalled(sdk)) {
              return new ValidationResult(PyBundle.message("runcfg.testing.no.test.framework", "py.test"),
                                          createQuickFix(sdk, facetErrorPanel, PyNames.PY_TEST));
            }
          }
          else if (PythonTestConfigurationsModel.PYTHONS_NOSETEST_NAME.equals(selectedItem)) {
            if (!VFSTestFrameworkListener.getInstance().isNoseTestInstalled(sdk)) {
              return new ValidationResult(PyBundle.message("runcfg.testing.no.test.framework", "nosetest"),
                                          createQuickFix(sdk, facetErrorPanel, PyNames.NOSE_TEST));
            }
          }
          else if (PythonTestConfigurationsModel.PYTHONS_ATTEST_NAME.equals(selectedItem)) {
            if (!VFSTestFrameworkListener.getInstance().isAtTestInstalled(sdk)) {
              return new ValidationResult(PyBundle.message("runcfg.testing.no.test.framework", "attest"),
                                          createQuickFix(sdk, facetErrorPanel, PyNames.AT_TEST));
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
        final PyPackageManagerImpl.UI ui = new PyPackageManagerImpl.UI(myProject, sdk, new PyPackageManagerImpl.UI.Listener() {
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
  public String getHelpTopic() {
    return "test_runner_configuration";
  }

  @Override
  public JComponent createComponent() {
    List<String> configurations = TestRunnerService.getInstance(myModule).getConfigurations();
    myModel = new PythonTestConfigurationsModel(configurations,
                                                TestRunnerService.getInstance(myModule).getProjectConfiguration(), myModule);

    updateConfigurations();
    initErrorValidation();
    return myMainPanel;
  }

  private void updateConfigurations() {
    myTestRunnerComboBox.setModel(myModel);
  }

  @Override
  public boolean isModified() {
    if (myTestRunnerComboBox.getSelectedItem() != myModel.getTestRunner()) {
      return true;
    }
    if (!Comparing.equal(myDocstringFormatComboBox.getSelectedItem(), myDocumentationSettings.myDocStringFormat)) {
      return true;
    }
    if (analyzeDoctest.isSelected() != myDocumentationSettings.analyzeDoctest) {
      return true;
    }
    if (!ReSTService.getInstance(myModule).getWorkdir().equals(myWorkDir.getText())) {
      return true;
    }
    if (!ReSTService.getInstance(myModule).txtIsRst() == txtIsRst.isSelected()) {
      return true;
    }
    if (!getRequirementsPath().equals(myRequirementsPathField.getText())) {
      return true;
    }
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
    if (!Comparing.equal(myDocstringFormatComboBox.getSelectedItem(), myDocumentationSettings.myDocStringFormat)) {
      DaemonCodeAnalyzer.getInstance(myProject).restart();
    }
    if (analyzeDoctest.isSelected() != myDocumentationSettings.analyzeDoctest) {
      final List<VirtualFile> files = Lists.newArrayList();
      ProjectRootManager.getInstance(myProject).getFileIndex().iterateContent(new ContentIterator() {
        @Override
        public boolean processFile(VirtualFile fileOrDir) {
          if (!fileOrDir.isDirectory() && PythonFileType.INSTANCE.getDefaultExtension().equals(fileOrDir.getExtension())) {
            files.add(fileOrDir);
          }
          return true;
        }
      });
      FileContentUtil.reparseFiles(myProject, Lists.newArrayList(files), false);
    }
    myModel.apply();
    myDocumentationSettings.myDocStringFormat = (String) myDocstringFormatComboBox.getSelectedItem();
    final ReSTService reSTService = ReSTService.getInstance(myModule);
    reSTService.setWorkdir(myWorkDir.getText());
    if (txtIsRst.isSelected() != reSTService.txtIsRst()) {
      reSTService.setTxtIsRst(txtIsRst.isSelected());
      reparseRstFiles();
    }
    myDocumentationSettings.analyzeDoctest = analyzeDoctest.isSelected();
    PyPackageRequirementsSettings.getInstance(myModule).setRequirementsPath(myRequirementsPathField.getText());
    DaemonCodeAnalyzer.getInstance(myProject).restart();
  }

  public void reparseRstFiles() {
    final List<VirtualFile> filesToReparse = Lists.newArrayList();
    ProjectRootManager.getInstance(myProject).getFileIndex().iterateContent(new ContentIterator() {
      @Override
      public boolean processFile(VirtualFile fileOrDir) {
        if (!fileOrDir.isDirectory() && PlainTextFileType.INSTANCE.getDefaultExtension().equals(fileOrDir.getExtension())) {
          filesToReparse.add(fileOrDir);
        }
        return true;
      }
    });
    FileContentUtilCore.reparseFiles(filesToReparse);
  }

  @Override
  public void reset() {
    myTestRunnerComboBox.setSelectedItem(myModel.getTestRunner());
    myTestRunnerComboBox.repaint();
    myModel.reset();
    myDocstringFormatComboBox.setSelectedItem(myDocumentationSettings.myDocStringFormat);
    myWorkDir.setText(ReSTService.getInstance(myModule).getWorkdir());
    txtIsRst.setSelected(ReSTService.getInstance(myModule).txtIsRst());
    analyzeDoctest.setSelected(myDocumentationSettings.analyzeDoctest);
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

