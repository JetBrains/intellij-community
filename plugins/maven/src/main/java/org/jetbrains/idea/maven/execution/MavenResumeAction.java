/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.execution;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunCanceledByUserException;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.VersionComparatorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenParsingContext;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.server.MavenDistribution;
import org.jetbrains.idea.maven.server.MavenDistributionsCache;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MavenResumeAction extends AnAction {

  private static final Logger LOG = Logger.getInstance(MavenResumeAction.class);

  private static final Set<String> PARAMS_DISABLING_RESUME = ContainerUtil.newHashSet("-rf", "-resume-from", "-pl", "-projects", "-am",
                                                                                      "-also-make", "-amd", "-also-make-dependents");

  public static final int STATE_INITIAL = 0;
  public static final int STATE_READING_PROJECT_LIST = 1;
  public static final int STATE_READING_PROJECT_LIST_OLD_MAVEN = 5;
  public static final int STATE_WAIT_FOR_BUILD = 2;
  public static final int STATE_WAIT_FOR______ = 3;
  public static final int STATE_WTF = -1;

  private final ProgramRunner myRunner;
  private final ExecutionEnvironment myEnvironment;
  private final MavenParsingContext myContext;

  private int myState = STATE_INITIAL;

  private int myBuildingProjectIndex = 0;

  private final List<String> myMavenProjectNames = new ArrayList<>();

  private String myResumeFromModuleName;

  private String myResumeModuleId;

  private String myMavenVersion;

  public MavenResumeAction(ProcessHandler processHandler,
                           ProgramRunner runner,
                           ExecutionEnvironment environment,
                           MavenParsingContext context) {
    super(RunnerBundle.message("maven.resume.from.title"), null, AllIcons.RunConfigurations.RerunFailedTests);
    myRunner = runner;
    myEnvironment = environment;
    myContext = context;

    final MavenRunConfiguration runConfiguration = (MavenRunConfiguration)environment.getRunProfile();
    myMavenVersion = getMavenVersion(runConfiguration);

    if (VersionComparatorUtil.compare(myMavenVersion, "3.5.3") < 0 || context == null) {
      processHandler.addProcessListener(new LegacyMavenResumeProcessAdapter(runConfiguration));
    }
    else {
      processHandler.addProcessListener(new MavenSpyResumeProcessAdapter(runConfiguration));
    }
  }

  private static String getMavenVersion(MavenRunConfiguration runConfiguration) {
    MavenGeneralSettings generalSettings = runConfiguration.getGeneralSettings();
    if (generalSettings == null) {
      MavenDistribution maven = MavenDistribution.fromSettings(runConfiguration.getProject());
      if (maven == null) {
        String version = MavenDistributionsCache.resolveEmbeddedMavenHome().getVersion();
        MavenLog.LOG
          .warn("Cannot determine maven version from run configuration and project settings, use embedded as version: " + version);
        return version;
      }
      return maven.getVersion();
    }
    else {
      return MavenUtil.getMavenVersion(generalSettings.getEffectiveMavenHome());
    }
  }

  private static boolean hasResumeFromParameter(MavenRunConfiguration runConfiguration) {
    List<String> goals = runConfiguration.getRunnerParameters().getGoals();
    return goals.size() > 2 && "-rf".equals(goals.get(goals.size() - 2));
  }

  @Nullable
  private MavenProject findProjectByName(@NotNull String projectName) {
    List<MavenProject> projects = MavenProjectsManager.getInstance(myEnvironment.getProject()).getProjects();

    MavenProject candidate = null;

    for (MavenProject mavenProject : projects) {
      if (projectName.equals(mavenProject.getName())) {
        if (candidate == null) {
          candidate = mavenProject;
        }
        else {
          return null;
        }
      }
    }

    if (candidate != null) {
      return candidate;
    }

    for (MavenProject mavenProject : projects) {
      String id = mavenProject.getMavenId().getGroupId() + ':' + mavenProject.getMavenId().getArtifactId() + ':' + mavenProject.getPackaging();
      if (projectName.contains(id)) {
        if (candidate == null) {
          candidate = mavenProject;
        }
        else {
          return null;
        }
      }
    }

    if (candidate != null) {
      return candidate;
    }

    for (MavenProject mavenProject : projects) {
      if (projectName.equals(mavenProject.getMavenId().getArtifactId())) {
        if (candidate == null) {
          candidate = mavenProject;
        }
        else {
          return null;
        }
      }
    }

    return candidate;
  }

  public static boolean isApplicable(@Nullable Project project, JavaParameters javaParameters, MavenRunConfiguration runConfiguration) {
    if (hasResumeFromParameter(runConfiguration)) { // This runConfiguration was created by other MavenResumeAction.
      MavenRunConfiguration clonedRunConf = runConfiguration.clone();
      List<String> clonedGoals = clonedRunConf.getRunnerParameters().getGoals();
      clonedGoals.remove(clonedGoals.size() - 1);
      clonedGoals.remove(clonedGoals.size() - 1);
      try {
        javaParameters = clonedRunConf.createJavaParameters(project);
      }
      catch (ExecutionException e) {
        return false;
      }
    }

    for (String params : javaParameters.getProgramParametersList().getList()) {
      if (PARAMS_DISABLING_RESUME.contains(params)) {
        return false;
      }
    }

    return true;
  }

  private static void log(String message) {
    if (ApplicationManager.getApplication().isInternal()) {
      LOG.error(message, new Exception());
    }
    else {
      LOG.warn(message, new Exception());
    }

  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (myResumeFromModuleName != null && myResumeModuleId != null) {
      e.getPresentation().setEnabled(true);
      e.getPresentation().setText(RunnerBundle.message("maven.resume.from.template",  myResumeFromModuleName));
    }
    else {
      e.getPresentation().setEnabled(false);
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = myEnvironment.getProject();
    try {
      MavenRunConfiguration runConfiguration = ((MavenRunConfiguration)myEnvironment.getRunProfile()).clone();

      List<String> goals = runConfiguration.getRunnerParameters().getGoals();

      if (goals.size() > 2 && "-rf".equals(goals.get(goals.size() - 2))) { // This runConfiguration was created by other MavenResumeAction.
        goals.set(goals.size() - 1, myResumeModuleId);
      }
      else {
        goals.add("-rf");
        goals.add(myResumeModuleId);
      }

      runConfiguration.getRunnerParameters().setGoals(goals);

      myRunner.execute(new ExecutionEnvironmentBuilder(myEnvironment).contentToReuse(null).runProfile(runConfiguration).build());
    }
    catch (RunCanceledByUserException ignore) {
    }
    catch (ExecutionException e1) {
      Messages.showErrorDialog(project, e1.getMessage(), ExecutionBundle.message("restart.error.message.title"));
    }
  }

  private class LegacyMavenResumeProcessAdapter extends ProcessAdapter {
    private final MavenRunConfiguration myRunConfiguration;

    LegacyMavenResumeProcessAdapter(MavenRunConfiguration runConfiguration) {myRunConfiguration = runConfiguration;}

    @Override
    public void processTerminated(@NotNull ProcessEvent event) {
      if (myState == STATE_WTF) return;

      if (event.getExitCode() == 0 && myBuildingProjectIndex != myMavenProjectNames.size()) {
        log(String.format("Build was success, but not all project was build. Project build order: %s, build index: %d",
                          myMavenProjectNames,
                          myBuildingProjectIndex));
      }

      if (event.getExitCode() == 1 && myBuildingProjectIndex > 0) {
        if (myBuildingProjectIndex == 1 && !hasResumeFromParameter(myRunConfiguration)) {
          return;
        }

        myResumeFromModuleName = myMavenProjectNames.get(myBuildingProjectIndex - 1);

        MavenProject mavenProject = findProjectByName(myResumeFromModuleName);
        if (mavenProject != null) {
          myResumeModuleId = mavenProject.getMavenId().getGroupId() + ':' + mavenProject.getMavenId().getArtifactId();
        }
      }
    }

    @Override
    public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
      if (outputType != ProcessOutputTypes.STDOUT) return;

      String text = event.getText().trim();
      if (text.isEmpty()) return;

      String textWithoutInfo = "";
      if (text.startsWith("[INFO] ")) {
        textWithoutInfo = text.substring("[INFO] ".length()).trim();
      }

      switch (myState) {
        case STATE_INITIAL: // initial state.
          if (textWithoutInfo.equals("Reactor build order:")) {
            myState = STATE_READING_PROJECT_LIST_OLD_MAVEN;
          }
          else if (textWithoutInfo.equals("Reactor Build Order:")) {
            myState = STATE_READING_PROJECT_LIST;
          }
          break;

        case STATE_READING_PROJECT_LIST:
          if (textWithoutInfo.equals("------------------------------------------------------------------------")) {
            myState = STATE_WAIT_FOR_BUILD;
          }
          else if (textWithoutInfo.length() > 0) {
            myMavenProjectNames.add(textWithoutInfo);
          }
          else if (!myMavenProjectNames.isEmpty()) {
            myState = STATE_WAIT_FOR______;
          }
          break;

        case STATE_READING_PROJECT_LIST_OLD_MAVEN:
          if (textWithoutInfo.length() > 0) {
            if (text.startsWith("[INFO]   ")) {
              myMavenProjectNames.add(textWithoutInfo);
            }
            else {
              myState = STATE_WAIT_FOR_BUILD;
            }
          }
          break;

        case STATE_WAIT_FOR_BUILD:
          if (textWithoutInfo.startsWith("Building ")) {
            String projectName = textWithoutInfo.substring("Building ".length());
            if (myBuildingProjectIndex >= myMavenProjectNames.size() ||
                !projectName.startsWith(myMavenProjectNames.get(myBuildingProjectIndex))) {
              myState = STATE_WTF;
              log(String.format("Invalid project building order. Defined order: %s, error index: %d, invalid line: %s",
                                myMavenProjectNames, myBuildingProjectIndex, text));
              break;
            }

            myBuildingProjectIndex++;
          }
          myState = STATE_WAIT_FOR______;
          break;

        case STATE_WAIT_FOR______:
          if (textWithoutInfo.equals("------------------------------------------------------------------------")) {
            myState = STATE_WAIT_FOR_BUILD;
          }
          break;

        case STATE_WTF:
          break;

        default:
          throw new IllegalStateException();
      }
    }
  }

  private class MavenSpyResumeProcessAdapter extends ProcessAdapter {
    private final MavenRunConfiguration myRunConfiguration;

    MavenSpyResumeProcessAdapter(MavenRunConfiguration runConfiguration) {

      myRunConfiguration = runConfiguration;
    }

    @Override
    public void processTerminated(@NotNull ProcessEvent event) {
      myContext.getStartedProjects();

      if (event.getExitCode() == 0 &&
          myContext.getStartedProjects().size() != 0 &&
          myContext.getProjectsInReactor().size() != myContext.getStartedProjects().size()) {
        log(String.format("Build was success, but not all project was build. Project build order: %s, built projects: %s",
                          myContext.getProjectsInReactor(),
                          myContext.getStartedProjects()));
      }
      if (event.getExitCode() != 0) {
        if (myContext.getStartedProjects().isEmpty()) {
          return;
        }
        if (myContext.getStartedProjects().size() == 1 && !hasResumeFromParameter(myRunConfiguration)) {
          return;
        }

        myResumeModuleId = myContext.getStartedProjects().get(myContext.getStartedProjects().size() - 1);
        String[] splitted = myResumeModuleId.split(":");
        if (splitted.length < 2) {
          myResumeFromModuleName = myResumeModuleId;
        }
        else {
          myResumeFromModuleName = splitted[1];
        }
      }
    }
  }
}
