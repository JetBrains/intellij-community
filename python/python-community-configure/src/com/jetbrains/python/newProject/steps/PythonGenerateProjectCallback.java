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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.platform.ProjectGeneratorPeer;
import com.intellij.util.BooleanFunction;
import com.jetbrains.python.newProject.PyNewProjectSettings;
import com.jetbrains.python.newProject.PythonProjectGenerator;
import com.jetbrains.python.sdk.PythonSdkAdditionalData;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PythonGenerateProjectCallback<T> extends AbstractNewProjectStep.AbstractCallback<T> {

  @Override
  public void consume(@Nullable ProjectSettingsStepBase<T> step, @NotNull ProjectGeneratorPeer<T> projectGeneratorPeer) {
    if (!(step instanceof ProjectSpecificSettingsStep)) return;

    final ProjectSpecificSettingsStep settingsStep = (ProjectSpecificSettingsStep)step;
    final DirectoryProjectGenerator generator = settingsStep.getProjectGenerator();
    Sdk sdk = settingsStep.getSdk();

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

    final Object settings = computeProjectSettings(generator, settingsStep, projectGeneratorPeer);
    final Project newProject = generateProject(settingsStep, settings);
    if (settings instanceof PyNewProjectSettings) {
      sdk = ((PyNewProjectSettings)settings).getSdk();
    }

    if (generator instanceof PythonProjectGenerator && sdk == null && newProject != null) {
      final PyNewProjectSettings newSettings = (PyNewProjectSettings)((PythonProjectGenerator)generator).getProjectSettings();
      ((PythonProjectGenerator)generator).createAndAddVirtualEnv(newProject, newSettings);
      sdk = newSettings.getSdk();
    }

    if (newProject != null && generator instanceof PythonProjectGenerator) {
      SdkConfigurationUtil.setDirectoryProjectSdk(newProject, sdk);
      final List<Sdk> sdks = PythonSdkType.getAllSdks();
      for (Sdk s : sdks) {
        final SdkAdditionalData additionalData = s.getSdkAdditionalData();
        if (additionalData instanceof PythonSdkAdditionalData) {
          ((PythonSdkAdditionalData)additionalData).reAssociateWithCreatedProject(newProject);
        }
      }
      ((PythonProjectGenerator)generator).afterProjectGenerated(newProject);
    }
  }

  @Nullable
  private static Project generateProject(@NotNull final ProjectSettingsStepBase settings, @Nullable Object generationSettings) {
    if (generationSettings == null) return null;
    final DirectoryProjectGenerator generator = settings.getProjectGenerator();
    final String location = FileUtil.expandUserHome(settings.getProjectLocation());
    return AbstractNewProjectStep.doGenerateProject(null, location, generator, generationSettings);
  }

  @Nullable
  public static Object computeProjectSettings(DirectoryProjectGenerator<?> generator,
                                              final ProjectSpecificSettingsStep settingsStep,
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
      newProjectSettings.setSdk(settingsStep.getSdk());
      newProjectSettings.setInstallFramework(settingsStep.installFramework());
      newProjectSettings.setRemotePath(settingsStep.getRemotePath());
    }
    return projectSettings;
  }
}
