// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProject.steps;

import com.intellij.execution.target.TargetEnvironmentConfiguration;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.util.projectWizard.AbstractNewProjectStep;
import com.intellij.ide.util.projectWizard.ProjectSettingsStepBase;
import com.intellij.ide.util.projectWizard.WebProjectTemplate;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.TextWithMnemonic;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.ui.HideableDecorator;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PathUtil;
import com.intellij.util.PlatformUtils;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.configuration.PyConfigurableInterpreterList;
import com.jetbrains.python.newProject.PyFrameworkProjectGenerator;
import com.jetbrains.python.newProject.PyNewProjectSettings;
import com.jetbrains.python.newProject.PythonProjectGenerator;
import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo;
import com.jetbrains.python.packaging.PyPackage;
import com.jetbrains.python.packaging.PyPackageUtil;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory;
import com.jetbrains.python.sdk.*;
import com.jetbrains.python.sdk.add.PyAddSdkGroupPanel;
import com.jetbrains.python.sdk.add.PyAddSdkPanel;
import com.jetbrains.python.sdk.add.v2.PythonInterpreterSelectionMode;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

public class ProjectSpecificSettingsStep<T extends PyNewProjectSettings> extends ProjectSettingsStepBase<T> implements DumbAware {
  private boolean myInstallFramework;
  private @Nullable PyAddSdkGroupPanel myInterpreterPanel;
  private @Nullable HideableDecorator myInterpretersDecorator;

  public ProjectSpecificSettingsStep(final @NotNull DirectoryProjectGenerator<T> projectGenerator,
                                     final @NotNull AbstractNewProjectStep.AbstractCallback<T> callback) {
    super(projectGenerator, callback);
  }

  @Override
  protected JPanel createAndFillContentPanel() {
    if (myProjectGenerator instanceof PythonProjectGenerator) {
      // Allow generator to display custom error
      ((PythonProjectGenerator<?>)myProjectGenerator).setErrorCallback(this::setErrorText);
    }
    return createContentPanelWithAdvancedSettingsPanel();
  }

  @Override
  protected @Nullable JPanel createAdvancedSettings() {
    JComponent advancedSettings = null;
    if (myProjectGenerator instanceof PythonProjectGenerator) {
      advancedSettings = ((PythonProjectGenerator<?>)myProjectGenerator).getSettingsPanel(myProjectDirectory.get());
    }
    else if (myProjectGenerator instanceof WebProjectTemplate) {
      advancedSettings = getPeer().getComponent();
    }
    if (advancedSettings != null) {
      final JPanel jPanel = new JPanel(new VerticalFlowLayout());
      final HideableDecorator deco = new HideableDecorator(jPanel, PyBundle.message("python.new.project.more.settings"), false);
      if (myProjectGenerator instanceof PythonProjectGenerator) {
        final ValidationResult result = ((PythonProjectGenerator<?>)myProjectGenerator).warningValidation(getInterpreterPanelSdk());
        deco.setOn(!result.isOk());
      }
      deco.setContentComponent(advancedSettings);
      return jPanel;
    }
    return null;
  }

  public @Nullable Sdk getSdk() {
    if (!(myProjectGenerator instanceof PythonProjectGenerator)) return null;
    final PyAddSdkGroupPanel interpreterPanel = myInterpreterPanel;
    if (interpreterPanel == null) return null;
    final PyAddSdkPanel panel = interpreterPanel.getSelectedPanel();
    if (panel instanceof PyAddNewEnvironmentPanel newEnvironmentPanel) {
      return new PyLazySdk("Uninitialized environment", newEnvironmentPanel::getOrCreateSdk);
    }
    else if (panel instanceof PyAddExistingSdkPanel) {
      return panel.getSdk();
    }
    else {
      return null;
    }
  }

  public @Nullable InterpreterStatisticsInfo getInterpreterInfoForStatistics() {
    if (myInterpreterPanel == null) return null;
    PyAddSdkPanel panel = myInterpreterPanel.getSelectedPanel();
    return panel.getStatisticInfo();
  }

  private @Nullable Sdk getInterpreterPanelSdk() {
    final PyAddSdkGroupPanel interpreterPanel = myInterpreterPanel;
    if (interpreterPanel == null) return null;
    return interpreterPanel.getSdk();
  }

  public boolean installFramework() {
    return myInstallFramework;
  }

  @Override
  protected void registerValidators() {
    super.registerValidators();
    if (myProjectGenerator instanceof PythonProjectGenerator) {
      addLocationChangeListener(event -> {
        final String fileName = PathUtil.getFileName(getProjectLocation());
        ((PythonProjectGenerator<?>)myProjectGenerator).locationChanged(fileName);
      });
    }
  }

  /**
   * @return path for project on remote side provided by user
   */
  public @Nullable String getRemotePath() {
    final PyAddSdkGroupPanel interpreterPanel = myInterpreterPanel;
    if (interpreterPanel == null) return null;
    final PyAddExistingSdkPanel panel = ObjectUtils.tryCast(interpreterPanel.getSelectedPanel(), PyAddExistingSdkPanel.class);
    if (panel == null) return null;
    return panel.getRemotePath();
  }

  @Override
  protected void initGeneratorListeners() {
    super.initGeneratorListeners();
    if (myProjectGenerator instanceof PythonProjectGenerator) {
      ((PythonProjectGenerator<?>)myProjectGenerator).addSettingsStateListener(this::checkValid);
      myErrorLabel.addMouseListener(((PythonProjectGenerator<?>)myProjectGenerator).getErrorLabelMouseListener());
    }
  }

  @Override
  public void setErrorText(@Nullable String text) {
    super.setErrorText(text);
    if (myInterpretersDecorator != null && !StringUtil.isEmpty(text)) myInterpretersDecorator.setOn(true);
  }

  @Override
  public void setWarningText(@Nullable String text) {
    super.setWarningText(text);
    if (myInterpretersDecorator != null && !StringUtil.isEmpty(text)) myInterpretersDecorator.setOn(true);
  }

  @Override
  public boolean checkValid() {
    myInstallFramework = false;
    if (!super.checkValid()) {
      return false;
    }

    var interpreterPanel = myInterpreterPanel;
    final Map<Boolean, List<String>> errorsAndWarnings = StreamEx
      .of(interpreterPanel == null ? Collections.emptyList() : interpreterPanel.validateAll())
      .groupingBy(it -> it.warning, Collectors.mapping(it -> it.message, Collectors.toList()));
    List<String> validationErrors = errorsAndWarnings.getOrDefault(false, Collections.emptyList());
    final List<String> validationWarnings = errorsAndWarnings.getOrDefault(true, Collections.emptyList());

    if (validationErrors.isEmpty()) {
      // Once can't create anything on immutable SDK
      var sdk = (interpreterPanel != null) ?  interpreterPanel.getSdk() : null;
      if (sdk != null && isImmutableSdk(sdk)) {
        validationErrors = List.of(
          PyBundle.message("python.unknown.project.synchronizer.this.interpreter.type.does.not.support.remote.project.creation"));
      }
    }

    if (!validationErrors.isEmpty()) {
      setErrorText(StringUtil.join(validationErrors, "\n"));
      return false;
    }
    else if (!validationWarnings.isEmpty()) {
      setWarningText(StreamEx.of(validationWarnings)
                       .map(HtmlChunk::raw)
                       .collect(HtmlChunk.toFragment(HtmlChunk.br()))
                       .toString());
    }

    final PythonProjectGenerator generator = ObjectUtils.tryCast(myProjectGenerator, PythonProjectGenerator.class);
    final Sdk sdk = getInterpreterPanelSdk();

    if (generator == null || sdk == null) {
      myInstallFramework = true;
      return true;
    }

    try {
      generator.checkProjectCanBeCreatedOnSdk(sdk, new File(getProjectLocation()));
    }
    catch (final PythonProjectGenerator.PyNoProjectAllowedOnSdkException e) {
      setErrorText(e.getMessage());
      return false;
    }

    final List<String> warnings = new ArrayList<>(validationWarnings);

    final PyFrameworkProjectGenerator frameworkGenerator = ObjectUtils.tryCast(myProjectGenerator, PyFrameworkProjectGenerator.class);

    if (frameworkGenerator != null) {

      // Framework package check may be heavy in case of remote sdk and should not be called on AWT, pretend everything is OK for
      // remote and check for packages later
      if (!PythonSdkUtil.isRemote(sdk)) {
        final Pair<Boolean, List<String>> validationInfo = validateFramework(frameworkGenerator, sdk);
        myInstallFramework = validationInfo.first;
        warnings.addAll(validationInfo.second);

        final ValidationResult warningResult = ((PythonProjectGenerator<?>)myProjectGenerator).warningValidation(sdk);
        if (!warningResult.isOk()) {
          warnings.add(warningResult.getErrorMessage());
        }
      }
    }

    if (!warnings.isEmpty()) {
      setWarningText(StreamEx.of(warnings)
                       .map(HtmlChunk::raw)
                       .collect(HtmlChunk.toFragment(HtmlChunk.br()))
                       .toString());
    }
    return true;
  }

  /**
   * See {@link PythonInterpreterTargetEnvironmentFactory#isMutable(TargetEnvironmentConfiguration)}
   */
  private static boolean isImmutableSdk(@NotNull Sdk sdk) {
    var targetConfig = PySdkExtKt.getTargetEnvConfiguration(sdk);
    if (targetConfig == null) {
      return false;
    }
    return !PythonInterpreterTargetEnvironmentFactory.Companion.isMutable(targetConfig);
  }


  private static @NotNull Pair<Boolean, List<String>> validateFramework(@NotNull PyFrameworkProjectGenerator generator, @NotNull Sdk sdk) {
    final List<String> warnings = new ArrayList<>();
    boolean installFramework = false;
    if (!generator.isFrameworkInstalled(sdk)) {
      final String frameworkName = generator.getFrameworkTitle();
      String message = PyBundle.message("python.package.installation.notification.message", frameworkName);
      if (PyPackageUtil.packageManagementEnabled(sdk, false, false)) {
        installFramework = true;
        final List<PyPackage> packages = PyPackageUtil.refreshAndGetPackagesModally(sdk);
        if (!PyPackageUtil.hasManagement(packages)) {
          message = PyBundle.message("python.package.and.packaging.tools.installation.notification.message", frameworkName);
        }
      }
      warnings.add(message);
    }
    return Pair.create(installFramework, warnings);
  }

  @Override
  protected JPanel createBasePanel() {
    if (myProjectGenerator instanceof PythonProjectGenerator) {
      final BorderLayout layout = new BorderLayout();

      final JPanel locationPanel = new JPanel(layout);

      final JPanel panel = new JPanel(new VerticalFlowLayout(0, 2));
      final LabeledComponent<TextFieldWithBrowseButton> location = createLocationComponent();

      locationPanel.add(location, BorderLayout.CENTER);
      panel.add(locationPanel);
      panel.add(createInterpretersPanel(((PythonProjectGenerator<?>)myProjectGenerator).getPreferredEnvironmentType()));

      final JPanel basePanelExtension = ((PythonProjectGenerator<?>)myProjectGenerator).extendBasePanel();
      if (basePanelExtension != null) {
        panel.add(basePanelExtension);
      }
      return panel;
    }

    return super.createBasePanel();
  }

  @NotNull
  private JPanel createInterpretersPanel(final @Nullable PythonInterpreterSelectionMode preferredEnvironment) {
    final JPanel container = new JPanel(new BorderLayout());
    final JPanel decoratorPanel = new JPanel(new VerticalFlowLayout());

    final List<Sdk> allExistingSdks = Arrays.asList(PyConfigurableInterpreterList.getInstance(null).getModel().getSdks());
    final List<Sdk> existingSdks = getValidPythonSdks(allExistingSdks);
    final Sdk preferredSdk = existingSdks.stream().findFirst().orElse(null);

    final String newProjectPath = getProjectLocation();
    final PyAddNewEnvironmentPanel newEnvironmentPanel = new PyAddNewEnvironmentPanel(allExistingSdks, newProjectPath, preferredEnvironment);
    final PyAddExistingSdkPanel existingSdkPanel = new PyAddExistingSdkPanel(null, null, existingSdks, newProjectPath, preferredSdk);

    PyAddSdkPanel defaultPanel = PySdkSettings.getInstance().getUseNewEnvironmentForNewProject() ?
                                 newEnvironmentPanel : existingSdkPanel;
    myInterpretersDecorator = new HideableDecorator(decoratorPanel, getProjectInterpreterTitle(defaultPanel).toString(), false);
    myInterpretersDecorator.setContentComponent(container);

    final List<PyAddSdkPanel> panels = Arrays.asList(newEnvironmentPanel, existingSdkPanel);
    myInterpreterPanel = new PyAddSdkGroupPanel(PyBundle.messagePointer("python.add.sdk.panel.name.new.project.interpreter"),
                                                getIcon(), panels, defaultPanel);
    myInterpreterPanel.addChangeListener(() -> {
      myInterpretersDecorator.setTitle(getProjectInterpreterTitle(myInterpreterPanel.getSelectedPanel()).toString());
      final boolean useNewEnvironment = myInterpreterPanel.getSelectedPanel() instanceof PyAddNewEnvironmentPanel;
      PySdkSettings.getInstance().setUseNewEnvironmentForNewProject(useNewEnvironment);
      checkValid();
    });

    addLocationChangeListener(event -> myInterpreterPanel.setNewProjectPath(getProjectLocation()));

    container.add(myInterpreterPanel, BorderLayout.NORTH);

    checkValid();

    return decoratorPanel;
  }

  private static @NotNull TextWithMnemonic getProjectInterpreterTitle(@NotNull PyAddSdkPanel panel) {
    final String name;
    if (panel instanceof PyAddNewEnvironmentPanel) {
      name = PyBundle.message("python.sdk.new.environment.kind", ((PyAddNewEnvironmentPanel)panel).getSelectedPanel().getEnvName());
    }
    else {
      final Sdk sdk = panel.getSdk();
      name = sdk != null ? sdk.getName() : panel.getPanelName();
    }
    return TextWithMnemonic.parse(PyBundle.message("python.sdk.python.interpreter.title.0", "[name]"))
      .replaceFirst("[name]", name);
  }

  public static @NotNull List<Sdk> getValidPythonSdks(@NotNull List<Sdk> existingSdks) {
    return StreamEx
      .of(existingSdks)
      .filter(sdk -> sdk != null && sdk.getSdkType() instanceof PythonSdkType && PySdkExtKt.getSdkSeemsValid(sdk))
      .sorted(new PreferredSdkComparator())
      .toList();
  }

  @Override
  protected @NotNull File findSequentNonExistingUntitled() {
    return Optional
      .ofNullable(PyUtil.as(myProjectGenerator, PythonProjectGenerator.class))
      .map(PythonProjectGenerator::getNewProjectPrefix)
      .map(it -> FileUtil.findSequentNonexistentFile(getBaseDir(), it, ""))
      .orElseGet(() -> super.findSequentNonExistingUntitled());
  }

  /**
   * If {@link PythonProjectGenerator} {@link PythonProjectGenerator#supportsWelcomeScript()},
   * {@link ProjectSpecificSettingsStep} and inheritors should ask use if one should be created, and return true if so.
   */
  @RequiresEdt
  public boolean createWelcomeScript() {
    return false;
  }

  private static @NotNull File getBaseDir() {
    if (PlatformUtils.isDataSpell() && Path.of(ProjectUtil.getBaseDir()).startsWith(PathManager.getConfigDir())) {
      return new File(ProjectUtil.getUserHomeProjectDir());
    }
    return new File(ProjectUtil.getBaseDir());
  }
}
