/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.edu.coursecreator.actions;

import com.intellij.execution.Executor;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.PathUtil;
import com.jetbrains.edu.coursecreator.CCProjectService;
import com.jetbrains.python.run.PythonConfigurationType;
import com.jetbrains.python.run.PythonRunConfiguration;
import org.jetbrains.annotations.NotNull;

public class PyCCRunTestsAction extends CCRunTestsAction {

  @Override
  protected void executeTests(@NotNull final Project project,
                                   @NotNull final VirtualFile virtualFile,
                                   @NotNull final VirtualFile taskDir,
                                   @NotNull final VirtualFile testFile) {
    final ConfigurationFactory factory = PythonConfigurationType.getInstance().getConfigurationFactories()[0];
    final RunnerAndConfigurationSettings settings =
      RunManager.getInstance(project).createRunConfiguration("test", factory);

    final PythonRunConfiguration configuration = (PythonRunConfiguration)settings.getConfiguration();
    configuration.setScriptName(testFile.getPath());
    configuration.setWorkingDirectory(taskDir.getPath());
    String taskFileName = CCProjectService.getRealTaskFileName(virtualFile.getName());
    if (taskFileName == null) {
      return;
    }
    VirtualFile userFile = taskDir.findChild(taskFileName);
    if (userFile == null) {
      return;
    }
    VirtualFile ideaDir = project.getBaseDir().findChild(".idea");
    if (ideaDir == null) {
      return;
    }
    VirtualFileManager.getInstance().refreshWithoutFileWatcher(true);
    ProjectView.getInstance(project).refresh();
    configuration.setScriptParameters(PathUtil.toSystemDependentName(project.getBasePath()) + " " + PathUtil.toSystemDependentName(userFile.getPath()));
    Executor executor = DefaultRunExecutor.getRunExecutorInstance();
    ProgramRunnerUtil.executeConfiguration(project, settings, executor);
  }
}
