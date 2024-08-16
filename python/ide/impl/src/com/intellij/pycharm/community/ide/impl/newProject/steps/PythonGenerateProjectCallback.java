// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.newProject.steps;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.projectWizard.AbstractNewProjectStep;
import com.intellij.ide.util.projectWizard.ProjectSettingsStepBase;
import com.intellij.ide.util.projectWizard.WebProjectTemplate;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.platform.ProjectGeneratorPeer;
import com.intellij.pycharm.community.ide.impl.newProject.welcome.PyWelcomeSettings;
import com.intellij.util.BooleanFunction;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.newProject.PyNewProjectSettings;
import com.jetbrains.python.newProject.PythonProjectGenerator;
import com.jetbrains.python.newProject.steps.ProjectSpecificSettingsStep;
import com.jetbrains.python.newProject.steps.PythonProjectSpecificSettingsStep;
import com.intellij.pycharm.community.ide.impl.newProject.welcome.PyWelcomeSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;


public final class PythonGenerateProjectCallback<T extends PyNewProjectSettings> extends AbstractNewProjectStep.AbstractCallback<T> {

  @Override
  public void consume(@Nullable ProjectSettingsStepBase<T> step, @NotNull ProjectGeneratorPeer<T> projectGeneratorPeer) {
    if (!(step instanceof ProjectSpecificSettingsStep settingsStep)) return;

    // FIXME: pass welcome script creation via settings
    if (settingsStep instanceof PythonProjectSpecificSettingsStep) {
      // has to be set before project generation
      boolean welcomeScript = PropertiesComponent.getInstance().getBoolean("PyCharm.NewProject.Welcome", false);
      PyWelcomeSettings.getInstance().setCreateWelcomeScriptForEmptyProject(welcomeScript);
    }

    final DirectoryProjectGenerator<?> generator = settingsStep.getProjectGenerator();
    Sdk sdk = settingsStep.getSdk();

    final Object settings = computeProjectSettings(generator, settingsStep, projectGeneratorPeer);
    final Project newProject = generateProject(settingsStep, settings);
    if (settings instanceof PyNewProjectSettings) {
      sdk = ((PyNewProjectSettings)settings).getSdk();
    }

    if (generator instanceof PythonProjectGenerator && sdk == null && newProject != null) {
      final PyNewProjectSettings newSettings = (PyNewProjectSettings)((PythonProjectGenerator<?>)generator).getProjectSettings();
      ((PythonProjectGenerator<?>)generator).createAndAddVirtualEnv(newProject, newSettings);
      sdk = newSettings.getSdk();
    }

    if (newProject != null && generator instanceof PythonProjectGenerator) {
      SdkConfigurationUtil.setDirectoryProjectSdk(newProject, sdk);
      ((PythonProjectGenerator<?>)generator).afterProjectGenerated(newProject);
    }

    if (settingsStep instanceof PythonProjectSpecificSettingsStep newStep) {
      // init git repostory
      if (PropertiesComponent.getInstance().getBoolean("PyCharm.NewProject.Git", false)) {
        ModuleManager moduleManager = ModuleManager.getInstance(newProject);
        Optional<Module> module = Arrays.stream(moduleManager.getModules()).findFirst();
        module.ifPresent((value -> {
          ModuleRootManager rootManager = ModuleRootManager.getInstance(value);
          Arrays.stream(rootManager.getContentRoots()).findFirst().ifPresent(root -> {
            PythonProjectSpecificSettingsStep.initializeGit(newProject, root);
          });
        }));
      }
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
  private static Object computeProjectSettings(DirectoryProjectGenerator<?> generator,
                                              final ProjectSpecificSettingsStep settingsStep,
                                              @NotNull final ProjectGeneratorPeer projectGeneratorPeer) {
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
      newProjectSettings.setInstallFramework(settingsStep.installFramework());
      newProjectSettings.setCreateWelcomeScript(settingsStep.createWelcomeScript());
      newProjectSettings.setRemotePath(settingsStep.getRemotePath());
    }
    return projectSettings;
  }
}
