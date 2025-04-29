// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProject.steps;

import com.intellij.execution.target.TargetEnvironmentConfiguration;
import com.intellij.ide.util.projectWizard.AbstractNewProjectStep;
import com.intellij.ide.util.projectWizard.ProjectSettingsStepBase;
import com.intellij.ide.util.projectWizard.WebProjectTemplate;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.TextWithMnemonic;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.ui.HideableDecorator;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PathUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.configuration.PyConfigurableInterpreterList;
import com.jetbrains.python.newProject.PyNewProjectSettings;
import com.jetbrains.python.newProject.PythonProjectGenerator;
import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo;
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
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @deprecated Use {@link com.jetbrains.python.newProjectWizard}
 */
@Deprecated(forRemoval = true)
public class ProjectSpecificSettingsStep<T extends PyNewProjectSettings> extends ProjectSettingsStepBase<T> implements DumbAware {
  private @Nullable PyAddSdkGroupPanel myInterpreterPanel;
  private @Nullable HideableDecorator myInterpretersDecorator;

  public ProjectSpecificSettingsStep(final @NotNull DirectoryProjectGenerator<T> projectGenerator,
                                     final @NotNull AbstractNewProjectStep.AbstractCallback<T> callback) {
    super(projectGenerator, callback, (projectGenerator instanceof PythonProjectGenerator<T> pyProjectGenerator)
                                      ? pyProjectGenerator.getNewProjectPrefix()
                                      : null);
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
    if (myProjectGenerator instanceof WebProjectTemplate) {
      advancedSettings = getPeer().getComponent(myLocationField, () -> checkValid());
    }
    if (advancedSettings != null) {
      final JPanel jPanel = new JPanel(new VerticalFlowLayout());
      final HideableDecorator deco = new HideableDecorator(jPanel, PyBundle.message("python.new.project.more.settings"), false);
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
      var sdk = (interpreterPanel != null) ? interpreterPanel.getSdk() : null;
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

      return panel;
    }

    return super.createBasePanel();
  }

  private @NotNull JPanel createInterpretersPanel(final @Nullable PythonInterpreterSelectionMode preferredEnvironment) {
    final JPanel container = new JPanel(new BorderLayout());
    final JPanel decoratorPanel = new JPanel(new VerticalFlowLayout());

    final List<Sdk> allExistingSdks = Arrays.asList(PyConfigurableInterpreterList.getInstance(null).getModel().getSdks());
    final List<Sdk> existingSdks = getValidPythonSdks(allExistingSdks);
    final Sdk preferredSdk = existingSdks.stream().findFirst().orElse(null);

    final String newProjectPath = getProjectLocation();
    final PyAddNewEnvironmentPanel newEnvironmentPanel =
      new PyAddNewEnvironmentPanel(allExistingSdks, newProjectPath, preferredEnvironment);
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
}
