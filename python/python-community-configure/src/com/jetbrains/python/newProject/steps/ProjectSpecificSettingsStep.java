/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.newProject.steps;

import com.google.common.collect.Iterables;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.ide.util.projectWizard.AbstractNewProjectStep;
import com.intellij.ide.util.projectWizard.ProjectSettingsStepBase;
import com.intellij.ide.util.projectWizard.WebProjectTemplate;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.HideableDecorator;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.jetbrains.python.configuration.PyConfigurableInterpreterList;
import com.jetbrains.python.newProject.PyFrameworkProjectGenerator;
import com.jetbrains.python.newProject.PythonProjectGenerator;
import com.jetbrains.python.packaging.PyPackage;
import com.jetbrains.python.packaging.PyPackageUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.sdk.*;
import com.jetbrains.python.sdk.add.PyAddSdkGroupPanel;
import com.jetbrains.python.sdk.add.PyAddSdkPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ProjectSpecificSettingsStep<T> extends ProjectSettingsStepBase<T> implements DumbAware {
  private boolean myInstallFramework;
  @Nullable private PyAddSdkGroupPanel myInterpreterPanel;

  public ProjectSpecificSettingsStep(@NotNull final DirectoryProjectGenerator<T> projectGenerator,
                                     @NotNull final AbstractNewProjectStep.AbstractCallback callback) {
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
  @Nullable
  protected JPanel createAdvancedSettings() {
    JComponent advancedSettings = null;
    if (myProjectGenerator instanceof PythonProjectGenerator) {
      advancedSettings = ((PythonProjectGenerator)myProjectGenerator).getSettingsPanel(myProjectDirectory);
    }
    else if (myProjectGenerator instanceof WebProjectTemplate) {
      advancedSettings = getPeer().getComponent();
    }
    if (advancedSettings != null) {
      final JPanel jPanel = new JPanel(new VerticalFlowLayout());
      final HideableDecorator deco = new HideableDecorator(jPanel, "Mor&e Settings", false);
      if (myProjectGenerator instanceof PythonProjectGenerator) {
        final ValidationResult result = ((PythonProjectGenerator)myProjectGenerator).warningValidation(getInterpreterPanelSdk());
        deco.setOn(!result.isOk());
      }
      deco.setContentComponent(advancedSettings);
      return jPanel;
    }
    return null;
  }

  @Nullable
  public Sdk getSdk() {
    if (!(myProjectGenerator instanceof PythonProjectGenerator)) return null;
    final PyAddSdkGroupPanel interpreterPanel = myInterpreterPanel;
    if (interpreterPanel == null) return null;
    final PyAddSdkPanel panel = interpreterPanel.getSelectedPanel();
    if (panel instanceof PyAddNewEnvironmentPanel) {
      final PyAddNewEnvironmentPanel newEnvironmentPanel = (PyAddNewEnvironmentPanel)panel;
      return new PyLazySdk("Uninitialized environment", newEnvironmentPanel::getOrCreateSdk);
    }
    else if (panel instanceof PyAddExistingSdkPanel) {
      return panel.getSdk();
    }
    else {
      return null;
    }
  }

  @Nullable
  private Sdk getInterpreterPanelSdk() {
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
        final String fileName = PathUtil.getFileName(getNewProjectPath());
        ((PythonProjectGenerator)myProjectGenerator).locationChanged(fileName);
      });
      final PyAddSdkGroupPanel interpreterPanel = myInterpreterPanel;
      if (interpreterPanel != null) {
        UiNotifyConnector.doWhenFirstShown(interpreterPanel, this::checkValid);
      }
    }
  }

  /**
   * @return path for project on remote side provided by user
   */
  @Nullable
  final String getRemotePath() {
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
      ((PythonProjectGenerator)myProjectGenerator).addSettingsStateListener(this::checkValid);
      myErrorLabel.addMouseListener(((PythonProjectGenerator)myProjectGenerator).getErrorLabelMouseListener());
    }
  }

  @Override
  public boolean checkValid() {
    myInstallFramework = false;
    if (!super.checkValid()) {
      return false;
    }

    final PyAddSdkGroupPanel interpreterPanel = myInterpreterPanel;
    if (interpreterPanel != null) {
      final List<ValidationInfo> validationInfos = interpreterPanel.validateAll();
      if (!validationInfos.isEmpty()) {
        setErrorText(StringUtil.join(validationInfos, info -> info.message, "\n"));
        return false;
      }
    }

    final PythonProjectGenerator generator = ObjectUtils.tryCast(myProjectGenerator, PythonProjectGenerator.class);
    final Sdk sdk = getInterpreterPanelSdk();

    if (generator == null || sdk == null) {
      myInstallFramework = true;
      return true;
    }

    try {
      generator.checkProjectCanBeCreatedOnSdk(sdk, new File(myLocationField.getText()));
    }
    catch (final PythonProjectGenerator.PyNoProjectAllowedOnSdkException e) {
      setErrorText(e.getMessage());
      return false;
    }

    final List<String> warnings = new ArrayList<>();

    final PyFrameworkProjectGenerator frameworkGenerator = ObjectUtils.tryCast(myProjectGenerator, PyFrameworkProjectGenerator.class);

    if (frameworkGenerator != null) {
      final String python3Error = validateFrameworkSupportsPython3(frameworkGenerator, sdk);
      if (python3Error != null) {
        setErrorText(python3Error);
        return false;
      }

      // Framework package check may be heavy in case of remote sdk and should not be called on AWT, pretend everything is OK for
      // remote and check for packages later
      if (!PythonSdkType.isRemote(sdk)) {
        final Pair<Boolean, List<String>> validationInfo = validateFramework(frameworkGenerator, sdk);
        myInstallFramework = validationInfo.first;
        warnings.addAll(validationInfo.second);

        final ValidationResult warningResult = ((PythonProjectGenerator)myProjectGenerator).warningValidation(sdk);
        if (!warningResult.isOk()) {
          warnings.add(warningResult.getErrorMessage());
        }
      }
    }

    if (!warnings.isEmpty()) {
      setWarningText(StringUtil.join(warnings, "<br/>"));
    }
    return true;
  }

  private static String validateFrameworkSupportsPython3(@NotNull PyFrameworkProjectGenerator generator, @NotNull Sdk sdk) {
    final String frameworkName = generator.getFrameworkTitle();
    final boolean isPy3k = PythonSdkType.getLanguageLevelForSdk(sdk).isPy3K();
    return isPy3k && !generator.supportsPython3() ? frameworkName + " is not supported for the selected interpreter" : null;
  }

  @NotNull
  private static Pair<Boolean, List<String>> validateFramework(@NotNull PyFrameworkProjectGenerator generator, @NotNull Sdk sdk) {
    final List<String> warnings = new ArrayList<>();
    boolean installFramework = false;
    if (!generator.isFrameworkInstalled(sdk)) {
      final String frameworkName = generator.getFrameworkTitle();
      if (PyPackageUtil.packageManagementEnabled(sdk)) {
        installFramework = true;
        final List<PyPackage> packages = PyPackageUtil.refreshAndGetPackagesModally(sdk);
        if (!PyPackageUtil.hasManagement(packages)) {
          warnings.add("Python packaging tools and " + frameworkName + " will be installed on the selected interpreter");
        }
        else {
          warnings.add(frameworkName + " will be installed on the selected interpreter");
        }
      }
      else {
        warnings.add(frameworkName + " is not installed on the selected interpreter");
      }
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
      panel.add(createInterpretersPanel(((PythonProjectGenerator)myProjectGenerator).getPreferredEnvironmentType()));

      final JPanel basePanelExtension = ((PythonProjectGenerator)myProjectGenerator).extendBasePanel();
      if (basePanelExtension != null) {
        panel.add(basePanelExtension);
      }
      return panel;
    }

    return super.createBasePanel();
  }

  @NotNull
  private JPanel createInterpretersPanel(@Nullable final String preferredEnvironment) {
    final JPanel container = new JPanel(new BorderLayout());
    final JPanel decoratorPanel = new JPanel(new VerticalFlowLayout());

    final List<Sdk> existingSdks = getValidPythonSdks();
    final Sdk preferredSdk = getPreferredSdk(existingSdks);

    final String newProjectPath = getNewProjectPath();
    final PyAddNewEnvironmentPanel newEnvironmentPanel = new PyAddNewEnvironmentPanel(existingSdks, newProjectPath, preferredEnvironment);
    final PyAddExistingSdkPanel existingSdkPanel = new PyAddExistingSdkPanel(null, existingSdks, newProjectPath, preferredSdk);

    final PyAddSdkPanel defaultPanel = PySdkSettings.getInstance().getUseNewEnvironmentForNewProject() ?
                                       newEnvironmentPanel : existingSdkPanel;
    final HideableDecorator decorator = new HideableDecorator(decoratorPanel, getProjectInterpreterTitle(defaultPanel), false);
    decorator.setContentComponent(container);

    final List<PyAddSdkPanel> panels = Arrays.asList(newEnvironmentPanel, existingSdkPanel);
    myInterpreterPanel = new PyAddSdkGroupPanel("New project interpreter", getIcon(), panels, defaultPanel);
    myInterpreterPanel.addChangeListener(() -> {
      decorator.setTitle(getProjectInterpreterTitle(myInterpreterPanel.getSelectedPanel()));
      final boolean useNewEnvironment = myInterpreterPanel.getSelectedPanel() instanceof PyAddNewEnvironmentPanel;
      PySdkSettings.getInstance().setUseNewEnvironmentForNewProject(useNewEnvironment);
      checkValid();
    });

    addLocationChangeListener(event -> myInterpreterPanel.setNewProjectPath(getNewProjectPath()));

    container.add(myInterpreterPanel, BorderLayout.NORTH);
    return decoratorPanel;
  }

  @NotNull
  private String getNewProjectPath() {
    final TextFieldWithBrowseButton field = myLocationField;
    if (field == null) return "";
    return field.getText().trim();
  }

  private void addLocationChangeListener(@NotNull Consumer<DocumentEvent> listener) {
    final TextFieldWithBrowseButton field = myLocationField;
    if (field == null) return;
    field.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        listener.consume(e);
      }
    });
  }

  @NotNull
  private static String getProjectInterpreterTitle(@NotNull PyAddSdkPanel panel) {
    final String name;
    if (panel instanceof PyAddNewEnvironmentPanel) {
      name = "New " + ((PyAddNewEnvironmentPanel)panel).getSelectedPanel().getEnvName() + " environment";
    }
    else {
      final Sdk sdk = panel.getSdk();
      name = sdk != null ? sdk.getName() : panel.getPanelName();
    }
    return "Project Interpreter: " + name;
  }

  @Nullable
  private Sdk getPreferredSdk(@NotNull List<Sdk> sdks) {
    final PyFrameworkProjectGenerator projectGenerator = ObjectUtils.tryCast(getProjectGenerator(), PyFrameworkProjectGenerator.class);
    final boolean onlyPython2 = projectGenerator != null && !projectGenerator.supportsPython3();
    final Sdk preferred = ContainerUtil.getFirstItem(sdks);
    if (preferred == null) return null;
    if (onlyPython2 && PythonSdkType.getLanguageLevelForSdk(preferred).isAtLeast(LanguageLevel.PYTHON30)) {
      final Sdk python2Sdk = PythonSdkType.findPython2Sdk(sdks);
      return python2Sdk != null ? python2Sdk : preferred;
    }
    return preferred;
  }

  @NotNull
  private static List<Sdk> getValidPythonSdks() {
    final List<Sdk> pythonSdks = PyConfigurableInterpreterList.getInstance(null).getAllPythonSdks();
    Iterables.removeIf(pythonSdks, sdk -> !(sdk.getSdkType() instanceof PythonSdkType) ||
                                          PythonSdkType.isInvalid(sdk) ||
                                          PySdkExtKt.getAssociatedProjectPath(sdk) != null);
    Collections.sort(pythonSdks, new PreferredSdkComparator());
    return pythonSdks;
  }
}
