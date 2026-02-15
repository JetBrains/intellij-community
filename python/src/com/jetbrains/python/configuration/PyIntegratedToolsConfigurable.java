// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.configuration;

import com.google.common.collect.Lists;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.execution.ExecutionException;
import com.intellij.facet.impl.ui.FacetErrorPanel;
import com.intellij.facet.ui.FacetConfigurationQuickFix;
import com.intellij.facet.ui.FacetEditorValidator;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.DefaultProjectFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.DialogPanel;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.python.community.impl.pipenv.PathKt;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.FileContentUtil;
import com.intellij.util.FileContentUtilCore;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.ReSTService;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.packaging.PyPackageManagerUI;
import com.jetbrains.python.packaging.PyPackageRequirementsSettings;
import com.jetbrains.python.packaging.PyRequirementsKt;
import com.jetbrains.python.packaging.requirementsTxt.PythonRequirementTxtSdkUtils;
import com.jetbrains.python.sdk.PythonSdkAdditionalData;
import com.jetbrains.python.sdk.legacy.PythonSdkUtil;
import com.jetbrains.python.sdk.pipenv.PipenvCommandExecutorKt;
import com.jetbrains.python.testing.PyAbstractTestFactory;
import com.jetbrains.python.testing.settings.PyTestRunConfigurationRenderer;
import com.jetbrains.python.testing.settings.PyTestRunConfigurationsModel;
import com.jetbrains.python.ui.PyUiUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.AbstractButton;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Insets;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;

public class PyIntegratedToolsConfigurable implements SearchableConfigurable {
  private final JPanel myMainPanel;
  private final JComboBox<PyAbstractTestFactory<?>> myTestRunnerComboBox;
  private final JComboBox<DocStringFormat> myDocstringFormatComboBox;
  private PyTestRunConfigurationsModel myModel;
  private final @Nullable Module myModule;
  private final @NotNull Project myProject;
  private final PyDocumentationSettings myDocumentationSettings;
  private final TextFieldWithBrowseButton myWorkDir;
  private final JCheckBox txtIsRst;
  private final JPanel myErrorPanel;
  private final TextFieldWithBrowseButton myRequirementsPathField;
  private final JCheckBox analyzeDoctest;
  private final JPanel myDocStringsPanel;
  private final JPanel myRestPanel;
  private final JCheckBox renderExternal;
  private final JPanel myPackagingPanel;
  private final JPanel myTestsPanel;
  private final TextFieldWithBrowseButton myPipEnvPathField;
  private final JPanel myPipEnvPanel;
  private final @NotNull Collection<@NotNull DialogPanel> myCustomizePanels = PyIntegratedToolsTestPanelCustomizer.Companion.createPanels();


  public PyIntegratedToolsConfigurable() {
    this(null, DefaultProjectFactory.getInstance().getDefaultProject());
  }

  public PyIntegratedToolsConfigurable(@NotNull Module module) {
    this(module, module.getProject());
  }

  private PyIntegratedToolsConfigurable(@Nullable Module module, @NotNull Project project) {
    myModule = module;
    myProject = project;
    {
      // GUI initializer generated by IntelliJ IDEA GUI Designer
      // >>> IMPORTANT!! <<<
      // DO NOT EDIT OR ADD ANY CODE HERE!
      myMainPanel = new JPanel();
      myMainPanel.setLayout(new GridLayoutManager(9, 1, new Insets(0, 0, 0, 0), -1, -1));
      final Spacer spacer1 = new Spacer();
      myMainPanel.add(spacer1, new GridConstraints(8, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                   GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
      myErrorPanel = new JPanel();
      myErrorPanel.setLayout(new BorderLayout(0, 0));
      myMainPanel.add(myErrorPanel, new GridConstraints(7, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                        null, null, 0, false));
      myDocStringsPanel = new JPanel();
      myDocStringsPanel.setLayout(new GridLayoutManager(3, 2, new Insets(0, 0, 0, 0), -1, -1));
      myMainPanel.add(myDocStringsPanel, new GridConstraints(3, 0, 2, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                             null, null, null, 0, false));
      final JBLabel jBLabel1 = new JBLabel();
      this.$$$loadLabelText$$$(jBLabel1, this.$$$getMessageFromBundle$$$("messages/PyBundle", "form.integrated.tools.docstring.format"));
      myDocStringsPanel.add(jBLabel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                          GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null,
                                                          null, 0, false));
      myDocstringFormatComboBox = new JComboBox();
      final DefaultComboBoxModel defaultComboBoxModel1 = new DefaultComboBoxModel();
      myDocstringFormatComboBox.setModel(defaultComboBoxModel1);
      myDocStringsPanel.add(myDocstringFormatComboBox,
                            new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                                false));
      analyzeDoctest = new JCheckBox();
      this.$$$loadButtonText$$$(analyzeDoctest, this.$$$getMessageFromBundle$$$("messages/PyBundle",
                                                                                "form.integrated.tools.analyze.python.code.in.docstrings"));
      myDocStringsPanel.add(analyzeDoctest, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      renderExternal = new JCheckBox();
      this.$$$loadButtonText$$$(renderExternal, this.$$$getMessageFromBundle$$$("messages/PyBundle",
                                                                                "form.integrated.tools.render.external.documentation.for.stdlib"));
      myDocStringsPanel.add(renderExternal, new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myRestPanel = new JPanel();
      myRestPanel.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
      myMainPanel.add(myRestPanel, new GridConstraints(5, 0, 2, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                       GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                       GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                       null, null, 0, false));
      final JBLabel jBLabel2 = new JBLabel();
      this.$$$loadLabelText$$$(jBLabel2,
                               this.$$$getMessageFromBundle$$$("messages/PyBundle", "form.integrated.tools.sphinx.working.directory"));
      myRestPanel.add(jBLabel2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                    GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                                    false));
      myWorkDir = new TextFieldWithBrowseButton();
      myRestPanel.add(myWorkDir, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                     GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null,
                                                     null, 0, false));
      txtIsRst = new JCheckBox();
      this.$$$loadButtonText$$$(txtIsRst, this.$$$getMessageFromBundle$$$("messages/PyBundle",
                                                                          "form.integrated.tools.treat.txt.files.as.restructuredtext"));
      myRestPanel.add(txtIsRst, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myPackagingPanel = new JPanel();
      myPackagingPanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
      myMainPanel.add(myPackagingPanel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                            null, null, null, 0, false));
      final JBLabel jBLabel3 = new JBLabel();
      this.$$$loadLabelText$$$(jBLabel3,
                               this.$$$getMessageFromBundle$$$("messages/PyBundle", "form.integrated.tools.package.requirements.file"));
      myPackagingPanel.add(jBLabel3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                         GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null,
                                                         null, 0, false));
      myRequirementsPathField = new TextFieldWithBrowseButton();
      myPackagingPanel.add(myRequirementsPathField,
                           new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                               GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                               false));
      myTestsPanel = new JPanel();
      myTestsPanel.setLayout(new BorderLayout(0, 0));
      myMainPanel.add(myTestsPanel, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                        null, null, 0, false));
      final JLabel label1 = new JLabel();
      this.$$$loadLabelText$$$(label1, this.$$$getMessageFromBundle$$$("messages/PyBundle", "form.integrated.tools.default.test.runner"));
      myTestsPanel.add(label1, BorderLayout.WEST);
      myTestRunnerComboBox = new JComboBox();
      myTestsPanel.add(myTestRunnerComboBox, BorderLayout.CENTER);
      myPipEnvPanel = new JPanel();
      myPipEnvPanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
      myMainPanel.add(myPipEnvPanel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                         null, null, 0, false));
      final JBLabel jBLabel4 = new JBLabel();
      this.$$$loadLabelText$$$(jBLabel4,
                               this.$$$getMessageFromBundle$$$("messages/PyBundle", "form.integrated.tools.path.to.pipenv.executable"));
      myPipEnvPanel.add(jBLabel4, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                      GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                      0, false));
      myPipEnvPathField = new TextFieldWithBrowseButton();
      myPipEnvPanel.add(myPipEnvPathField, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                               GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                               null, null, 0, false));
      jBLabel1.setLabelFor(myDocstringFormatComboBox);
      jBLabel2.setLabelFor(myDocstringFormatComboBox);
    }
    myDocumentationSettings = PyDocumentationSettings.getInstance(myModule);
    PyPackageRequirementsSettings packagingSettings = PyPackageRequirementsSettings.getInstance(module);
    myDocstringFormatComboBox.setModel(new CollectionComboBoxModel<>(new ArrayList<>(Arrays.asList(DocStringFormat.values())),
                                                                     myDocumentationSettings.getFormat()));
    myDocstringFormatComboBox.setRenderer(SimpleListCellRenderer.create("", DocStringFormat::getName));

    myWorkDir.addBrowseFolderListener(myProject, FileChooserDescriptorFactory.createSingleFolderDescriptor()
      .withTitle(PyBundle.message("configurable.choose.working.directory")));
    ReSTService service = ReSTService.getInstance(myModule);
    myWorkDir.setText(service.getWorkdir());
    txtIsRst.setSelected(service.txtIsRst());
    analyzeDoctest.setSelected(myDocumentationSettings.isAnalyzeDoctest());
    renderExternal.setSelected(myDocumentationSettings.isRenderExternalDocumentation());
    myRequirementsPathField.addBrowseFolderListener(myProject, FileChooserDescriptorFactory.createSingleLocalFileDescriptor()
      .withTitle(PyBundle.message("configurable.choose.path.to.the.package.requirements.file")));
    myRequirementsPathField.setText(getRequirementsPath());

    myPipEnvPathField.addBrowseFolderListener(null, FileChooserDescriptorFactory.createSingleFileDescriptor());

    myDocStringsPanel.setBorder(IdeBorderFactory.createTitledBorder(PyBundle.message("integrated.tools.configurable.docstrings")));
    myRestPanel.setBorder(IdeBorderFactory.createTitledBorder(PyBundle.message("integrated.tools.configurable.restructuredtext")));
    myPackagingPanel.setBorder(IdeBorderFactory.createTitledBorder(PyBundle.message("integrated.tools.configurable.packaging")));
    myTestsPanel.setBorder(IdeBorderFactory.createTitledBorder(PyBundle.message("integrated.tools.configurable.testing")));
    myPipEnvPanel.setBorder(IdeBorderFactory.createTitledBorder(PyBundle.message("integrated.tools.configurable.pipenv")));
  }

  private static Method $$$cachedGetBundleMethod$$$ = null;

  /** @noinspection ALL */
  private String $$$getMessageFromBundle$$$(String path, String key) {
    ResourceBundle bundle;
    try {
      Class<?> thisClass = this.getClass();
      if ($$$cachedGetBundleMethod$$$ == null) {
        Class<?> dynamicBundleClass = thisClass.getClassLoader().loadClass("com.intellij.DynamicBundle");
        $$$cachedGetBundleMethod$$$ = dynamicBundleClass.getMethod("getBundle", String.class, Class.class);
      }
      bundle = (ResourceBundle)$$$cachedGetBundleMethod$$$.invoke(null, path, thisClass);
    }
    catch (Exception e) {
      bundle = ResourceBundle.getBundle(path);
    }
    return bundle.getString(key);
  }

  /** @noinspection ALL */
  private void $$$loadLabelText$$$(JLabel component, String text) {
    StringBuffer result = new StringBuffer();
    boolean haveMnemonic = false;
    char mnemonic = '\0';
    int mnemonicIndex = -1;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '&') {
        i++;
        if (i == text.length()) break;
        if (!haveMnemonic && text.charAt(i) != '&') {
          haveMnemonic = true;
          mnemonic = text.charAt(i);
          mnemonicIndex = result.length();
        }
      }
      result.append(text.charAt(i));
    }
    component.setText(result.toString());
    if (haveMnemonic) {
      component.setDisplayedMnemonic(mnemonic);
      component.setDisplayedMnemonicIndex(mnemonicIndex);
    }
  }

  /** @noinspection ALL */
  private void $$$loadButtonText$$$(AbstractButton component, String text) {
    StringBuffer result = new StringBuffer();
    boolean haveMnemonic = false;
    char mnemonic = '\0';
    int mnemonicIndex = -1;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '&') {
        i++;
        if (i == text.length()) break;
        if (!haveMnemonic && text.charAt(i) != '&') {
          haveMnemonic = true;
          mnemonic = text.charAt(i);
          mnemonicIndex = result.length();
        }
      }
      result.append(text.charAt(i));
    }
    component.setText(result.toString());
    if (haveMnemonic) {
      component.setMnemonic(mnemonic);
      component.setDisplayedMnemonicIndex(mnemonicIndex);
    }
  }

  /** @noinspection ALL */
  public JComponent $$$getRootComponent$$$() { return myMainPanel; }

  private @NotNull String getRequirementsPath() {
    if (myModule == null) {
      return "";
    }
    Sdk sdk = PythonSdkUtil.findPythonSdk(myModule);
    if (sdk == null) {
      return "";
    }
    SdkAdditionalData data = sdk.getSdkAdditionalData();
    if (!(data instanceof PythonSdkAdditionalData)) {
      return "";
    }
    Path requiredTxtPath = ((PythonSdkAdditionalData)data).getRequiredTxtPath();
    final String path = requiredTxtPath != null ? requiredTxtPath.toString() : "";
    return path;
  }

  private void setRequirementsPath(String requirementsPath) {
    if (myModule == null) {
      return;
    }

    Sdk sdk = PythonSdkUtil.findPythonSdk(myModule);
    if (sdk == null) {
      return;
    }
    try {
      PythonRequirementTxtSdkUtils.saveRequirementsTxtPath(myModule.getProject(), sdk, Path.of(requirementsPath));
    }
    catch (Throwable t) {
      Logger.getInstance(PyIntegratedToolsConfigurable.class).warn("Failed to save requirements path", t);
    }
  }

  private void initErrorValidation() {
    final FacetErrorPanel facetErrorPanel = new FacetErrorPanel();
    myErrorPanel.add(facetErrorPanel.getComponent(), BorderLayout.CENTER);

    facetErrorPanel.getValidatorsManager().registerValidator(new FacetEditorValidator() {
      @Override
      public @NotNull ValidationResult check() {
        final Sdk sdk = PythonSdkUtil.findPythonSdk(myModule);
        if (sdk != null) {
          var factory = myModel.getSelected();
          if (factory != null && !factory.isFrameworkInstalled(myProject, sdk)) {
            return new ValidationResult(PyBundle.message("runcfg.testing.no.test.framework", factory.getName()),
              // isFrameworkInstalled() == false => getPackageSpec() != null
                                        createQuickFix(sdk, facetErrorPanel,
                                                       Objects.requireNonNull(factory.getPackageSpec()).getPackageName()));
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
          public void started() { }

          @Override
          public void finished(List<ExecutionException> exceptions) {
            if (exceptions.isEmpty()) {
              facetErrorPanel.getValidatorsManager().validate();
            }
          }
        });
        ui.install(Collections.singletonList(PyRequirementsKt.pyRequirement(name, null)), Collections.emptyList());
      }
    };
  }


  @Override
  public @Nls String getDisplayName() {
    return PyBundle.message("configurable.PyIntegratedToolsConfigurable.display.name");
  }

  @Override
  public String getHelpTopic() {
    return "test_runner_configuration";
  }

  @Override
  public JComponent createComponent() {
    myModel = PyTestRunConfigurationsModel.Companion.create(myModule);

    if (myModule != null) {
      Project project = myModule.getProject();
      myTestRunnerComboBox.setRenderer(new PyTestRunConfigurationRenderer(PythonSdkUtil.findPythonSdk(myModule), project));
    }

    for (@NotNull DialogPanel panel : myCustomizePanels) {
      myTestsPanel.add(BorderLayout.AFTER_LAST_LINE, panel);
    }

    updateConfigurations();
    initErrorValidation();
    var pane = new JBScrollPane(myMainPanel);
    pane.setViewportBorder(JBUI.Borders.empty());
    pane.setBorder(JBUI.Borders.empty());
    return pane;
  }

  private void updateConfigurations() {
    //noinspection unchecked
    myTestRunnerComboBox.setModel(myModel);
  }

  @Override
  public boolean isModified() {
    if (!Objects.equals(myTestRunnerComboBox.getSelectedItem(), myModel.getTestRunner())) {
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
    if (!myPipEnvPathField.getText()
      .equals(StringUtil.notNullize(PathKt.getPipenvPath(PropertiesComponent.getInstance())))) {
      return true;
    }
    return ContainerUtil.exists(myCustomizePanels, panel -> panel.isModified());
  }

  @Override
  public void apply() throws ConfigurationException {
    if (myDocstringFormatComboBox.getSelectedItem() != myDocumentationSettings.getFormat()) {
      DaemonCodeAnalyzer.getInstance(myProject).restart(this);
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
    setRequirementsPath(myRequirementsPathField.getText());

    DaemonCodeAnalyzer.getInstance(myProject).restart(this);
    PathKt.setPipenvPath(PropertiesComponent.getInstance(), StringUtil.nullize(myPipEnvPathField.getText()));

    for (@NotNull DialogPanel panel : myCustomizePanels) {
      panel.apply();
    }
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

    DaemonCodeAnalyzer.getInstance(myProject).restart(this);
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
      final String savedPath = PathKt.getPipenvPath(PropertiesComponent.getInstance());
      if (savedPath != null) {
        pipEnvText.setText(savedPath);
      }
      else {
        final Path executable = PipenvCommandExecutorKt.detectPipEnvExecutableOrNull();
        if (executable != null) {
          pipEnvText.getEmptyText().setText(PyBundle.message("configurable.pipenv.auto.detected", executable.toString()));
        }
      }
    }

    for (@NotNull DialogPanel panel : myCustomizePanels) {
      panel.reset();
    }
  }

  @Override
  public @NotNull String getId() {
    return "PyIntegratedToolsConfigurable";
  }
}
