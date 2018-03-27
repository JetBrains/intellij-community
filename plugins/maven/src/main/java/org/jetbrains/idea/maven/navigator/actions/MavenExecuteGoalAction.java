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
package org.jetbrains.idea.maven.navigator.actions;

import com.intellij.execution.configurations.ParametersList;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.execution.*;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenSettings;
import org.jetbrains.idea.maven.utils.MavenUtil;

import javax.swing.event.HyperlinkEvent;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public class MavenExecuteGoalAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    final Project project = e.getRequiredData(CommonDataKeys.PROJECT);

    ExecuteMavenGoalHistoryService historyService = ExecuteMavenGoalHistoryService.getInstance(project);

    MavenExecuteGoalDialog dialog = new MavenExecuteGoalDialog(project, historyService.getHistory());

    String lastWorkingDirectory = historyService.getWorkDirectory();
    if (lastWorkingDirectory.length() == 0) {
      lastWorkingDirectory = obtainAppropriateWorkingDirectory(project);
    }

    dialog.setWorkDirectory(lastWorkingDirectory);

    if (StringUtil.isEmptyOrSpaces(historyService.getCanceledCommand())) {
      if (historyService.getHistory().size() > 0) {
        dialog.setGoals(historyService.getHistory().get(0));
      }
    }
    else {
      dialog.setGoals(historyService.getCanceledCommand());
    }

    if (!dialog.showAndGet()) {
      historyService.setCanceledCommand(dialog.getGoals());
      return;
    }

    historyService.setCanceledCommand(null);

    String goals = dialog.getGoals();
    goals = goals.trim();
    if (goals.startsWith("mvn ")) {
      goals = goals.substring("mvn ".length()).trim();
    }

    String workDirectory = dialog.getWorkDirectory();

    historyService.addCommand(goals, workDirectory);

    MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(project);

    File mavenHome = MavenUtil.resolveMavenHomeDirectory(projectsManager.getGeneralSettings().getMavenHome());
    if (mavenHome == null) {
      Notification notification = new Notification(MavenUtil.MAVEN_NOTIFICATION_GROUP,
                                                   "Failed to execute goal",
                                                   RunnerBundle.message("external.maven.home.no.default.with.fix"), NotificationType.ERROR,
                                                   new NotificationListener.Adapter() {
                                                     @Override
                                                     protected void hyperlinkActivated(@NotNull Notification notification,
                                                                                       @NotNull HyperlinkEvent e) {
                                                       ShowSettingsUtil.getInstance()
                                                         .showSettingsDialog(project, MavenSettings.DISPLAY_NAME);
                                                     }
                                                   });

      Notifications.Bus.notify(notification, project);
      return;
    }

    MavenRunnerParameters parameters =
      new MavenRunnerParameters(true, workDirectory, (String)null, Arrays.asList(ParametersList.parse(goals)), Collections.emptyList());

    MavenGeneralSettings generalSettings = new MavenGeneralSettings();
    generalSettings.setMavenHome(mavenHome.getPath());

    MavenRunnerSettings runnerSettings = MavenRunner.getInstance(project).getSettings().clone();
    runnerSettings.setMavenProperties(new LinkedHashMap<>());
    runnerSettings.setSkipTests(false);

    MavenRunConfigurationType.runConfiguration(project, parameters, generalSettings, runnerSettings, null);
  }

  private static String obtainAppropriateWorkingDirectory(@NotNull Project project) {
    List<MavenProject> rootProjects = MavenProjectsManager.getInstance(project).getRootProjects();
    if (rootProjects.isEmpty()) return "";

    return rootProjects.get(0).getDirectory();
  }
}
