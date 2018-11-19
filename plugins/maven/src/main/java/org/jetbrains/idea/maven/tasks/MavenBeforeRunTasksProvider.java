// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.tasks;

import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.execution.ParametersListUtil;
import icons.MavenIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.execution.MavenEditGoalDialog;
import org.jetbrains.idea.maven.execution.MavenRunner;
import org.jetbrains.idea.maven.execution.MavenRunnerParameters;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenLog;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.util.Pair.pair;

public class MavenBeforeRunTasksProvider extends BeforeRunTaskProvider<MavenBeforeRunTask> {
  public static final Key<MavenBeforeRunTask> ID = Key.create("Maven.BeforeRunTask");
  private final Project myProject;

  public MavenBeforeRunTasksProvider(Project project) {
    myProject = project;
  }

  @Override
  public Key<MavenBeforeRunTask> getId() {
    return ID;
  }

  @Override
  public String getName() {
    return TasksBundle.message("maven.tasks.before.run.empty");
  }

  @Override
  public Icon getIcon() {
    return MavenIcons.MavenLogo;
  }

  @Nullable
  @Override
  public Icon getTaskIcon(MavenBeforeRunTask task) {
    return MavenIcons.MavenLogo;
  }

  @Override
  public String getDescription(MavenBeforeRunTask task) {
    MavenProject mavenProject = getMavenProject(task);
    if (mavenProject == null) {
      return TasksBundle.message("maven.tasks.before.run.empty");
    }

    String desc = mavenProject.getDisplayName() + ": " + StringUtil.notNullize(task.getGoal()).trim();
    return TasksBundle.message("maven.tasks.before.run", desc);
  }

  @Nullable
  private MavenProject getMavenProject(MavenBeforeRunTask task) {
    String pomXmlPath = task.getProjectPath();
    if (StringUtil.isEmpty(pomXmlPath)) return null;

    VirtualFile file = LocalFileSystem.getInstance().findFileByPath(pomXmlPath);
    if (file == null) return null;

    return MavenProjectsManager.getInstance(myProject).findProject(file);
  }

  @Override
  public boolean isConfigurable() {
    return true;
  }

  @Override
  public MavenBeforeRunTask createTask(@NotNull RunConfiguration runConfiguration) {
    return new MavenBeforeRunTask();
  }

  @Override
  public boolean configureTask(@NotNull RunConfiguration runConfiguration, @NotNull MavenBeforeRunTask task) {
    MavenEditGoalDialog dialog = new MavenEditGoalDialog(myProject);

    dialog.setTitle(TasksBundle.message("maven.tasks.select.goal.title"));

    if (task.getGoal() == null) {
      // just created empty task.
      MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(myProject);
      List<MavenProject> rootProjects = projectsManager.getRootProjects();
      if (rootProjects.size() > 0) {
        dialog.setSelectedMavenProject(rootProjects.get(0));
      }
      else {
        dialog.setSelectedMavenProject(null);
      }
    }
    else {
      String goals = splitToGoalsAndPomFileName(task.getGoal()).first;

      MavenProject mavenProject = getMavenProject(task);
      if (mavenProject != null) {
        String pomFileName = mavenProject.getFile().getName();
        if (FileUtil.namesEqual(pomFileName, MavenConstants.POM_XML)) {
          dialog.setGoals(goals);
        }
        else {
          dialog.setGoals(goals + " -f " + ParametersListUtil.join(pomFileName));
        }
        dialog.setSelectedMavenProject(mavenProject);
      }
      else {
        dialog.setGoals(goals);
        dialog.setSelectedMavenProject(null);
      }
    }

    if (!dialog.showAndGet()) {
      return false;
    }

    Pair<String, String> goalsAndPomFileName = splitToGoalsAndPomFileName(dialog.getGoals());
    task.setProjectPath(dialog.getWorkDirectory() + "/" + goalsAndPomFileName.second);
    task.setGoal(goalsAndPomFileName.first);
    return true;
  }

  @Override
  public boolean canExecuteTask(@NotNull RunConfiguration configuration, @NotNull MavenBeforeRunTask task) {
    return task.getGoal() != null && task.getProjectPath() != null;
  }

  @Override
  public boolean executeTask(@NotNull final DataContext context,
                             @NotNull RunConfiguration configuration,
                             @NotNull ExecutionEnvironment env,
                             @NotNull final MavenBeforeRunTask task) {
    final Semaphore targetDone = new Semaphore();
    final boolean[] result = new boolean[]{true};
    try {
      ApplicationManager.getApplication().invokeAndWait(() -> {
        final Project project = CommonDataKeys.PROJECT.getData(context);
        final MavenProject mavenProject = getMavenProject(task);

        if (project == null || project.isDisposed() || mavenProject == null) return;

        FileDocumentManager.getInstance().saveAllDocuments();

        final MavenExplicitProfiles explicitProfiles = MavenProjectsManager.getInstance(project).getExplicitProfiles();
        final MavenRunner mavenRunner = MavenRunner.getInstance(project);

        targetDone.down();
        new Task.Backgroundable(project, TasksBundle.message("maven.tasks.executing"), true) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            try {
              MavenRunnerParameters params = new MavenRunnerParameters(
                true,
                mavenProject.getDirectory(),
                mavenProject.getFile().getName(),
                ParametersListUtil.parse(task.getGoal()),
                explicitProfiles.getEnabledProfiles(),
                explicitProfiles.getDisabledProfiles());

              result[0] = mavenRunner.runBatch(Collections.singletonList(params),
                                            null,
                                            null,
                                            TasksBundle.message("maven.tasks.executing"),
                                            indicator);
            }
            finally {
              targetDone.up();
            }
          }

          @Override
          public boolean shouldStartInBackground() {
            return MavenRunner.getInstance(project).getSettings().isRunMavenInBackground();
          }

          @Override
          public void processSentToBackground() {
            MavenRunner.getInstance(project).getSettings().setRunMavenInBackground(true);
          }
        }.queue();
      }, ModalityState.NON_MODAL);
    }
    catch (Exception e) {
      MavenLog.LOG.error(e);
      return false;
    }
    targetDone.waitFor();
    return result[0];
  }

  @NotNull
  private static Pair<String, String> splitToGoalsAndPomFileName(@Nullable String goals) {
    if (goals == null) {
      return pair(null, MavenConstants.POM_XML);
    }
    List<String> commandLine = ParametersListUtil.parse(goals);
    int pomFileNameIndex = 1 + commandLine.indexOf("-f");
    if (pomFileNameIndex != 0 && pomFileNameIndex < commandLine.size()) {
      String pomFileName = commandLine.remove(pomFileNameIndex);
      commandLine.remove(pomFileNameIndex - 1);
      return pair(ParametersListUtil.join(commandLine), pomFileName);
    }
    return pair(goals, MavenConstants.POM_XML);
  }
}
