// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.configuration;

import com.google.common.collect.Lists;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.execution.ExecutionException;
import com.intellij.facet.impl.ui.FacetErrorPanel;
import com.intellij.facet.ui.FacetConfigurationQuickFix;
import com.intellij.facet.ui.FacetEditorValidator;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.DefaultProjectFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.FileContentUtil;
import com.intellij.util.FileContentUtilCore;
import com.intellij.util.ObjectUtils;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.ReSTService;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.packaging.PyPackageManagerUI;
import com.jetbrains.python.packaging.PyPackageRequirementsSettings;
import com.jetbrains.python.packaging.PyPackageUtil;
import com.jetbrains.python.packaging.PyRequirementsKt;
import com.jetbrains.python.sdk.PythonSdkUtil;
import com.jetbrains.python.sdk.pipenv.PipenvKt;
import com.jetbrains.python.testing.PyTestFrameworkService;
import com.jetbrains.python.testing.PythonTestConfigurationsModel;
import com.jetbrains.python.testing.TestRunnerService;
import com.jetbrains.python.testing.VFSTestFrameworkListener;
import com.jetbrains.python.ui.PyUiUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PyIntegratedToolsConfigurable implements SearchableConfigurable {
  private JPanel myMainPanel;
  private JComboBox myTestRunnerComboBox;
  private JComboBox<DocStringFormat> myDocstringFormatComboBox;
  private PythonTestConfigurationsModel myModel;
  private PyPackageRequirementsSettings myPackagingSettings;
  @Nullable private final Module myModule;
  @NotNull private final Project myProject;
  private final PyDocumentationSettings myDocumentationSettings;
  private TextFieldWithBrowseButton myWorkDir;
  private JCheckBox txtIsRst;
  private JPanel myErrorPanel;
  private TextFieldWithBrowseButton myRequirementsPathField;
  private JCheckBox analyzeDoctest;
  private JPanel myDocStringsPanel;
  private JPanel myRestPanel;
  private JCheckBox renderExternal;
  private JPanel myPackagingPanel;
  private JPanel myTestsPanel;
  private TextFieldWithBrowseButton myPipEnvPathField;
  private JPanel myPipEnvPanel;


  public PyIntegratedToolsConfigurable() {
    this(null, DefaultProjectFactory.getInstance().getDefaultProject());
  }

  public PyIntegratedToolsConfigurable(@NotNull Module module) {
    this(module, module.getProject());
  }

  private PyIntegratedToolsConfigurable(@Nullable Module module, @NotNull Project project) {
    myModule = module;
    myProject = project;
    myDocumentationSettings = PyDocumentationSettings.getInstance(myModule);
    myPackagingSettings = PyPackageRequirementsSettings.getInstance(module);
    myDocstringFormatComboBox.setModel(new CollectionComboBoxModel<>(Arrays.asList(DocStringFormat.values()),
                                                                     myDocumentationSettings.getFormat()));
    myDocstringFormatComboBox.setRenderer(SimpleListCellRenderer.create("", DocStringFormat::getName));

    final FileChooserDescriptor fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    myWorkDir.addBrowseFolderListener(PyBundle.message("configurable.choose.working.directory"), null, myProject, fileChooserDescriptor);
    ReSTService service = ReSTService.getInstance(myModule);
    myWorkDir.setText(service.getWorkdir());
    txtIsRst.setSelected(service.txtIsRst());
    analyzeDoctest.setSelected(myDocumentationSettings.isAnalyzeDoctest());
    renderExternal.setSelected(myDocumentationSettings.isRenderExternalDocumentation());
    myRequirementsPathField.addBrowseFolderListener(PyBundle.message("configurable.choose.path.to.the.package.requirements.file"), null, myProject,
                                                    FileChooserDescriptorFactory.createSingleLocalFileDescriptor());
    myRequirementsPathField.setText(getRequirementsPath());

    myPipEnvPathField.addBrowseFolderListener(null, null, null, FileChooserDescriptorFactory.createSingleFileDescriptor());

    myDocStringsPanel.setBorder(IdeBorderFactory.createTitledBorder(PyBundle.message("integrated.tools.configurable.docstrings")));
    myRestPanel.setBorder(IdeBorderFactory.createTitledBorder(PyBundle.message("integrated.tools.configurable.restructuredtext")));
    myPackagingPanel.setBorder(IdeBorderFactory.createTitledBorder(PyBundle.message("integrated.tools.configurable.packaging")));
    myTestsPanel.setBorder(IdeBorderFactory.createTitledBorder(PyBundle.message("integrated.tools.configurable.testing")));
    myPipEnvPanel.setBorder(IdeBorderFactory.createTitledBorder(PyBundle.message("integrated.tools.configurable.pipenv")));
  }

  @NotNull
  private String getRequirementsPath() {
    final String path = myPackagingSettings.getRequirementsPath();
    if (myModule != null && myPackagingSettings.isDefaultPath() && !PyPackageUtil.hasRequirementsTxt(myModule)) {
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
      @NotNull
      @Override
      public ValidationResult check() {
        final Sdk sdk = PythonSdkUtil.findPythonSdk(myModule);
        if (sdk != null) {
          final Object selectedItem = myTestRunnerComboBox.getSelectedItem();

          for (final String framework : PyTestFrameworkService.getFrameworkNamesArray()) {
            if (PyTestFrameworkService.getSdkReadableNameByFramework(framework).equals(selectedItem)) {
              if (!VFSTestFrameworkListener.getInstance().isTestFrameworkInstalled(sdk, framework)) {
                return new ValidationResult(PyBundle.message("runcfg.testing.no.test.framework", framework),
                                            createQuickFix(sdk, facetErrorPanel, framework));
              }
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
        final PyPackageManagerUI ui = new PyPackageManagerUI(myProject, sdk, new PyPackageManagerUI.Listener() {
          @Override
          public void started() {}

          @Override
          public void finished(List<ExecutionException> exceptions) {
            if (exceptions.isEmpty()) {
              VFSTestFrameworkListener.getInstance().setTestFrameworkInstalled(true, sdk.getHomePath(),
                                                                               name);
              facetErrorPanel.getValidatorsManager().validate();
            }
          }
        });
        ui.install(Collections.singletonList(PyRequirementsKt.pyRequirement(name)), Collections.emptyList());
      }
    };
  }


  @Nls
  @Override
  public String getDisplayName() {
    return PyBundle.message("configurable.PyIntegratedToolsConfigurable.display.name");
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
    //noinspection unchecked
    myTestRunnerComboBox.setModel(myModel);
  }

  @Override
  public boolean isModified() {
    if (myTestRunnerComboBox.getSelectedItem() != myModel.getTestRunner()) {
      return true;
    }
    if (myDocstringFormatComboBox.getSelectedItem() != myDocumentationSettings.getFormat()) {
      return true;
    }
    if (analyzeDoctest.isSelected() != myDocumentationSettings.isAnalyzeDoctest()) {
      return true;
    }
    if (renderExternal.isSelected() != myDocumentationSettings.isRenderExternalDocumentation()) {
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
    if (!myPipEnvPathField.getText().equals(StringUtil.notNullize(PipenvKt.getPipEnvPath(PropertiesComponent.getInstance())))) {
      return true;
    }
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
    if (myDocstringFormatComboBox.getSelectedItem() != myDocumentationSettings.getFormat()) {
      DaemonCodeAnalyzer.getInstance(myProject).restart();
    }
    if (analyzeDoctest.isSelected() != myDocumentationSettings.isAnalyzeDoctest()) {
      final List<VirtualFile> files = new ArrayList<>();
      ProjectRootManager.getInstance(myProject).getFileIndex().iterateContent(fileOrDir -> {
        if (!fileOrDir.isDirectory() && PythonFileType.INSTANCE.getDefaultExtension().equals(fileOrDir.getExtension())) {
          files.add(fileOrDir);
        }
        return true;
      });
      FileContentUtil.reparseFiles(myProject, Lists.newArrayList(files), false);
    }
    myModel.apply();
    myDocumentationSettings.setRenderExternalDocumentation(renderExternal.isSelected());
    myDocumentationSettings.setFormat((DocStringFormat)myDocstringFormatComboBox.getSelectedItem());
    final ReSTService reSTService = ReSTService.getInstance(myModule);
    reSTService.setWorkdir(myWorkDir.getText());
    if (txtIsRst.isSelected() != reSTService.txtIsRst()) {
      reSTService.setTxtIsRst(txtIsRst.isSelected());
      reparseFiles(Collections.singletonList(PlainTextFileType.INSTANCE.getDefaultExtension()));
    }
    myDocumentationSettings.setAnalyzeDoctest(analyzeDoctest.isSelected());
    myPackagingSettings.setRequirementsPath(myRequirementsPathField.getText());

    DaemonCodeAnalyzer.getInstance(myProject).restart();
    PipenvKt.setPipEnvPath(PropertiesComponent.getInstance(), StringUtil.nullize(myPipEnvPathField.getText()));
  }

  public void reparseFiles(final List<String> extensions) {
    final List<VirtualFile> filesToReparse = new ArrayList<>();
    ProjectRootManager.getInstance(myProject).getFileIndex().iterateContent(fileOrDir -> {
      if (!fileOrDir.isDirectory() && extensions.contains(fileOrDir.getExtension())) {
        filesToReparse.add(fileOrDir);
      }
      return true;
    });
    FileContentUtilCore.reparseFiles(filesToReparse);

    PyUiUtil.rehighlightOpenEditors(myProject);

    DaemonCodeAnalyzer.getInstance(myProject).restart();
  }

  @Override
  public void reset() {
    myTestRunnerComboBox.setSelectedItem(myModel.getTestRunner());
    myTestRunnerComboBox.repaint();
    myModel.reset();
    myDocstringFormatComboBox.setSelectedItem(myDocumentationSettings.getFormat());
    myWorkDir.setText(ReSTService.getInstance(myModule).getWorkdir());
    txtIsRst.setSelected(ReSTService.getInstance(myModule).txtIsRst());
    analyzeDoctest.setSelected(myDocumentationSettings.isAnalyzeDoctest());
    renderExternal.setSelected(myDocumentationSettings.isRenderExternalDocumentation());
    myRequirementsPathField.setText(getRequirementsPath());
    // TODO: Move pipenv settings into a separate configurable
    final JBTextField pipEnvText = ObjectUtils.tryCast(myPipEnvPathField.getTextField(), JBTextField.class);
    if (pipEnvText != null) {
      final String savedPath = PipenvKt.getPipEnvPath(PropertiesComponent.getInstance());
      if (savedPath != null) {
        pipEnvText.setText(savedPath);
      }
      else {
        final File executable = PipenvKt.detectPipEnvExecutable();
        if (executable != null) {
          pipEnvText.getEmptyText().setText(PyBundle.message("configurable.pipenv.auto.detected", executable.getAbsolutePath()));
        }
      }
    }
  }

  @NotNull
  @Override
  public String getId() {
    return "PyIntegratedToolsConfigurable";
  }
}

