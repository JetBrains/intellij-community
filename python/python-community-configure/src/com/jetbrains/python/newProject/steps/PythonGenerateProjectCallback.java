/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.ide.util.projectWizard.AbstractNewProjectStep;
import com.intellij.ide.util.projectWizard.ProjectSettingsStepBase;
import com.intellij.ide.util.projectWizard.WebProjectTemplate;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.platform.ProjectGeneratorPeer;
import com.intellij.util.BooleanFunction;
import com.jetbrains.python.configuration.PyConfigurableInterpreterList;
import com.jetbrains.python.newProject.PyNewProjectSettings;
import com.jetbrains.python.newProject.PythonProjectGenerator;
import com.jetbrains.python.sdk.PyDetectedSdk;
import com.jetbrains.python.sdk.PythonSdkAdditionalData;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.PythonSdkUpdater;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PythonGenerateProjectCallback<T> extends AbstractNewProjectStep.AbstractCallback<T> {
  private static final Logger LOG = Logger.getInstance(PythonGenerateProjectCallback.class);

  @Override
  public void consume(@Nullable ProjectSettingsStepBase<T> step, @NotNull ProjectGeneratorPeer<T> projectGeneratorPeer) {
    if (!(step instanceof ProjectSpecificSettingsStep)) return;

    final ProjectSpecificSettingsStep settingsStep = (ProjectSpecificSettingsStep)step;
    final DirectoryProjectGenerator generator = settingsStep.getProjectGenerator();
    Sdk sdk = settingsStep.getSdk();

    if (sdk instanceof PyDetectedSdk) {
      sdk = addDetectedSdk(settingsStep, sdk);
      if (PythonSdkType.isVirtualEnv(sdk)) {
        final SdkModificator sdkModificator = sdk.getSdkModificator();
        final PythonSdkAdditionalData additionalData = new PythonSdkAdditionalData(PythonSdkFlavor.getFlavor(sdk));
        additionalData.associateWithNewProject();
        sdkModificator.setSdkAdditionalData(additionalData);
        ApplicationManager.getApplication().runWriteAction(sdkModificator::commitChanges);
      }
    }

    if (generator instanceof PythonProjectGenerator) {
      final BooleanFunction<PythonProjectGenerator> beforeProjectGenerated = ((PythonProjectGenerator)generator).beforeProjectGenerated(sdk);
      if (beforeProjectGenerated != null) {
        final boolean result = beforeProjectGenerated.fun((PythonProjectGenerator)generator);
        if (!result) {
          Messages.showWarningDialog("Project can not be generated", "Error in Project Generation");
          return;
        }
      }
    }
    final Project newProject = generateProject(settingsStep, projectGeneratorPeer);
    if (generator instanceof PythonProjectGenerator && sdk == null && newProject != null) {
      final PyNewProjectSettings settings = (PyNewProjectSettings)((PythonProjectGenerator)generator).getProjectSettings();
      ((PythonProjectGenerator)generator).createAndAddVirtualEnv(newProject, settings);
      sdk = settings.getSdk();
    }

    if (newProject != null && generator instanceof PythonProjectGenerator) {
      SdkConfigurationUtil.setDirectoryProjectSdk(newProject, sdk);
      final List<Sdk> sdks = PythonSdkType.getAllSdks();
      for (Sdk s : sdks) {
        final SdkAdditionalData additionalData = s.getSdkAdditionalData();
        if (additionalData instanceof PythonSdkAdditionalData) {
          ((PythonSdkAdditionalData)additionalData).reassociateWithCreatedProject(newProject);
        }
      }
    }
  }

  private static Sdk addDetectedSdk(ProjectSpecificSettingsStep settingsStep, Sdk sdk) {
    final Project project = ProjectManager.getInstance().getDefaultProject();
    final ProjectSdksModel model = PyConfigurableInterpreterList.getInstance(project).getModel();
    final String name = sdk.getName();
    VirtualFile sdkHome =
      WriteAction.compute(() -> LocalFileSystem.getInstance().refreshAndFindFileByPath(name));
    sdk = SdkConfigurationUtil.createAndAddSDK(sdkHome.getPath(), PythonSdkType.getInstance());
    if (sdk != null) {
      PythonSdkUpdater.updateOrShowError(sdk, null, project, null);
    }

    model.addSdk(sdk);
    settingsStep.setSdk(sdk);
    try {
      model.apply();
    }
    catch (ConfigurationException exception) {
      LOG.error("Error adding detected python interpreter " + exception.getMessage());
    }
    return sdk;
  }

  @Nullable
  private static Project generateProject(@NotNull final ProjectSettingsStepBase settings,
                                         @NotNull final ProjectGeneratorPeer projectGeneratorPeer) {
    final DirectoryProjectGenerator generator = settings.getProjectGenerator();
    final String location = FileUtil.expandUserHome(settings.getProjectLocation());
    return AbstractNewProjectStep.doGenerateProject(null, location, generator,
                                                    computeProjectSettings(generator, (ProjectSpecificSettingsStep)settings, projectGeneratorPeer));
  }

  public static Object computeProjectSettings(DirectoryProjectGenerator<?> generator,
                                              final ProjectSpecificSettingsStep settings,
                                              @NotNull final ProjectGeneratorPeer projectGeneratorPeer) {
    Object projectSettings = null;
    if (generator instanceof PythonProjectGenerator) {
      final PythonProjectGenerator<?> projectGenerator = (PythonProjectGenerator<?>)generator;
      projectSettings = projectGenerator.getProjectSettings();
    }
    else if (generator instanceof WebProjectTemplate) {
      projectSettings = projectGeneratorPeer.getSettings();
    }
    if (projectSettings instanceof PyNewProjectSettings) {
      final PyNewProjectSettings newProjectSettings = (PyNewProjectSettings)projectSettings;
      newProjectSettings.setSdk(settings.getSdk());
      newProjectSettings.setInstallFramework(settings.installFramework());
      newProjectSettings.setRemotePath(settings.getRemotePath());
    }
    return projectSettings;
  }
}
