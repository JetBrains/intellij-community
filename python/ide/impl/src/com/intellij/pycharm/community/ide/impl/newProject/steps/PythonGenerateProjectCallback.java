// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.newProject.steps;

import com.intellij.ide.util.projectWizard.AbstractNewProjectStep;
import com.intellij.ide.util.projectWizard.ProjectSettingsStepBase;
import com.intellij.ide.util.projectWizard.WebProjectTemplate;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.platform.ProjectGeneratorPeer;
import com.jetbrains.python.newProject.PyNewProjectSettings;
import com.jetbrains.python.newProject.PythonProjectGenerator;
import com.jetbrains.python.newProject.steps.ProjectSpecificSettingsStep;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated See {@link com.jetbrains.python.newProjectWizard}
 */
@Deprecated(forRemoval = true)
public final class PythonGenerateProjectCallback<T extends PyNewProjectSettings> extends AbstractNewProjectStep.AbstractCallback<T> {

  @Override
  public void consume(@Nullable ProjectSettingsStepBase<T> step, @NotNull ProjectGeneratorPeer<T> projectGeneratorPeer) {
    if (!(step instanceof ProjectSpecificSettingsStep settingsStep)) return;

    // FIXME: pass welcome script creation via settings

    final DirectoryProjectGenerator<?> generator = settingsStep.getProjectGenerator();
    Sdk sdk = settingsStep.getSdk();

    final Object settings = computeProjectSettings(generator, settingsStep, projectGeneratorPeer);
    final Project newProject = generateProject(settingsStep, settings);
    if (settings instanceof PyNewProjectSettings) {
      sdk = ((PyNewProjectSettings)settings).getSdk();
    }

    if (generator instanceof PythonProjectGenerator && sdk == null && newProject != null) {
      final PyNewProjectSettings newSettings = (PyNewProjectSettings)((PythonProjectGenerator<?>)generator).getProjectSettings();
      sdk = newSettings.getSdk();
    }

    if (newProject != null && generator instanceof PythonProjectGenerator) {
      SdkConfigurationUtil.setDirectoryProjectSdk(newProject, sdk);
      ((PythonProjectGenerator<?>)generator).afterProjectGenerated(newProject);
    }
  }

  private static @Nullable Project generateProject(final @NotNull ProjectSettingsStepBase settings, @Nullable Object generationSettings) {
    if (generationSettings == null) return null;
    final DirectoryProjectGenerator generator = settings.getProjectGenerator();
    final String location = FileUtil.expandUserHome(settings.getProjectLocation());
    return AbstractNewProjectStep.doGenerateProject(null, location, generator, generationSettings);
  }

  private static @Nullable Object computeProjectSettings(DirectoryProjectGenerator<?> generator,
                                                         final ProjectSpecificSettingsStep settingsStep,
                                                         final @NotNull ProjectGeneratorPeer projectGeneratorPeer) {
    Object projectSettings = null;
    if (generator instanceof PythonProjectGenerator<?> projectGenerator) {
      projectSettings = projectGenerator.getProjectSettings();
    }
    else if (generator instanceof WebProjectTemplate) {
      projectSettings = projectGeneratorPeer.getSettings();
    }
    if (projectSettings instanceof PyNewProjectSettings newProjectSettings) {
      newProjectSettings.setSdk(settingsStep.getSdk());
      newProjectSettings.setInterpreterInfoForStatistics(settingsStep.getInterpreterInfoForStatistics());
      newProjectSettings.setRemotePath(settingsStep.getRemotePath());
    }
    return projectSettings;
  }
}
