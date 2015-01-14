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
package com.jetbrains.edu.learning;

import com.intellij.execution.RunContentExecutor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.learning.course.Task;
import com.jetbrains.python.run.PythonTracebackFilter;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class PyStudyUtils implements StudyUtilsExtensionPoint {

  @Override
  public Sdk findSdk(@NotNull final Project project) {
    return PythonSdkType.findPythonSdk(ModuleManager.getInstance(project).getModules()[0]);
  }

  @Nullable
  @Override
  public String getLinkToTutorial() {
    return "<html>If you'd like to learn" +
           " more about PyCharm " +
           "Educational Edition, " +
           "click <a href=\"https://www.jetbrains.com/pycharm-educational/quickstart/\">here</a> to watch a tutorial</html>";
  }

  @Override
  public StudyTestRunner getTestRunner(@NotNull final Task task, @NotNull final VirtualFile taskDir) {
    return new PyStudyTestRunner(task, taskDir);
  }

  @Override
  public RunContentExecutor getExecutor(@NotNull final Project project, @NotNull final ProcessHandler handler) {
    return new RunContentExecutor(project, handler).withFilter(new PythonTracebackFilter(project));
  }

  @Override
  public void setCommandLineParameters(@NotNull final GeneralCommandLine cmd,
                                               @NotNull final Project project,
                                               @NotNull final String filePath,
                                               @NotNull final String pythonPath,
                                               @NotNull final Task currentTask) {
    if (!currentTask.getUserTests().isEmpty()) {
      cmd.addParameter(new File(project.getBaseDir().getPath(), StudyNames.USER_TESTER).getPath());
      cmd.addParameter(pythonPath);
      cmd.addParameter(filePath);
    }
    else {
      cmd.addParameter(filePath);
    }
  }


}
