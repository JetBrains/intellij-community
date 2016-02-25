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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.courseFormat.Task;
import com.jetbrains.edu.learning.checker.StudyExecutor;
import com.jetbrains.edu.learning.checker.StudyTestRunner;
import com.jetbrains.edu.learning.courseFormat.UserTest;
import com.jetbrains.python.run.PythonTracebackFilter;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;
import java.io.File;
import java.util.List;

public class PyStudyExecutor implements StudyExecutor {

  @Override
  public Sdk findSdk(@NotNull final Project project) {
    return PythonSdkType.findPythonSdk(ModuleManager.getInstance(project).getModules()[0]);
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
                                               @NotNull final String sdkPath,
                                               @NotNull final Task currentTask) {
    final List<UserTest> userTests = StudyTaskManager.getInstance(project).getUserTests(currentTask);
    if (!userTests.isEmpty()) {
      StudyLanguageManager manager = StudyUtils.getLanguageManager(currentTask.getLesson().getCourse());
      if (manager != null) {
        cmd.addParameter(new File(project.getBaseDir().getPath(), manager.getUserTester()).getPath());
        cmd.addParameter(sdkPath);
        cmd.addParameter(filePath);
      }
    }
    else {
      cmd.addParameter(filePath);
    }
  }

  public void showNoSdkNotification(@NotNull final Project project) {
    final String text = "<html>No Python interpreter configured for the project<br><a href=\"\">Configure interpreter</a></html>";
    final BalloonBuilder balloonBuilder = JBPopupFactory.getInstance().
      createHtmlTextBalloonBuilder(text, null,
                                   MessageType.WARNING.getPopupBackground(),
                                   event -> {
                                     if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                                       ApplicationManager.getApplication()
                                         .invokeLater(
                                           () -> ShowSettingsUtil.getInstance().showSettingsDialog(project, "Project Interpreter"));
                                     }
                                   });
    balloonBuilder.setHideOnLinkClick(true);
    final Balloon balloon = balloonBuilder.createBalloon();
    StudyUtils.showCheckPopUp(project, balloon);
  }
}
