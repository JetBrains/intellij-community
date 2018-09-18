// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution;

import com.intellij.debugger.impl.DebuggerManagerImpl;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.*;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.util.ExecutionErrorDialog;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.execution.util.ProgramParametersUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.task.ExecuteRunConfigurationTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.execution.build.MavenExecutionEnvironmentProvider;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.util.containers.ContainerUtil.indexOf;

/**
 * @author ibessonov
 */
public class MavenApplicationConfigurationExecutionEnvironmentProvider implements MavenExecutionEnvironmentProvider {

  @Override
  public boolean isApplicable(@NotNull ExecuteRunConfigurationTask task) {
    return task.getRunProfile() instanceof ApplicationConfiguration;
  }

  @Override
  @Nullable
  public ExecutionEnvironment createExecutionEnvironment(@NotNull Project project, @NotNull ExecuteRunConfigurationTask task,
                                                         @Nullable Executor executor) {
    ConfigurationFactory configurationFactory = MavenRunConfigurationType.getInstance().getConfigurationFactories()[0];
    ApplicationConfiguration applicationConfiguration = (ApplicationConfiguration)task.getRunProfile();
    String mainClassName = applicationConfiguration.getMainClassName();
    if (isEmpty(mainClassName)) {
      return null;
    }

    Module module = applicationConfiguration.getConfigurationModule().getModule();
    if (module == null) {
      return null;
    }

    MavenProject mavenProject = MavenProjectsManager.getInstance(project).findProject(module);
    if (mavenProject == null) {
      return null;
    }

    MavenRunConfiguration mavenRunConfiguration = new MyExecRunConfiguration(project, configurationFactory, applicationConfiguration);

    mavenRunConfiguration.setBeforeRunTasks(applicationConfiguration.getBeforeRunTasks());
    copyLogParameters(applicationConfiguration, mavenRunConfiguration);

    MavenRunnerParameters runnerParameters = mavenRunConfiguration.getRunnerParameters();
    runnerParameters.setWorkingDirPath(mavenProject.getDirectory());
    runnerParameters.setPomFileName(mavenProject.getFile().getName());

    JavaParameters javaParameters = new JavaParameters();
    JavaParametersUtil.configureConfiguration(javaParameters, applicationConfiguration);

    ParametersList execArgs = new ParametersList();
    execArgs.addAll(javaParameters.getVMParametersList().getList());
    execArgs.add("-classpath");
    execArgs.add("%classpath");
    execArgs.add(mainClassName);
    execArgs.addParametersString(applicationConfiguration.getProgramParameters());

    String execExecutable = getJdkExecPath(applicationConfiguration);
    if (execExecutable == null) {
      throw new RuntimeException(ExecutionBundle.message("run.configuration.cannot.find.vm.executable"));
    }

    String workingDirectory = ProgramParametersUtil.getWorkingDir(applicationConfiguration, project, module);

    List<String> goals = runnerParameters.getGoals();
    if (isNotEmpty(workingDirectory)) {
      goals.add("-Dexec.workingdir=" + workingDirectory);
    }
    goals.add("-Dexec.args=" + execArgs.getParametersString());
    goals.add("-Dexec.executable=" + toSystemDependentName(execExecutable));
    goals.add("exec:exec");

    if (executor == null) {
      executor = DefaultRunExecutor.getRunExecutorInstance();
    }
    return new ExecutionEnvironmentBuilder(project, executor).runProfile(mavenRunConfiguration).build();
  }

  private static String getJdkExecPath(@NotNull ApplicationConfiguration applicationConfiguration) {
    Project project = applicationConfiguration.getProject();
    try {
      Sdk jdk = JavaParametersUtil.createProjectJdk(project, applicationConfiguration.getAlternativeJrePath());
      if (jdk == null) {
        throw new RuntimeException(ExecutionBundle.message("run.configuration.error.no.jdk.specified"));
      }

      SdkTypeId type = jdk.getSdkType();
      if (!(type instanceof JavaSdkType)) {
        throw new RuntimeException(ExecutionBundle.message("run.configuration.error.no.jdk.specified"));
      }

      return ((JavaSdkType)type).getVMExecutablePath(jdk);
    }
    catch (CantRunException e) {
      ExecutionErrorDialog.show(e, "Cannot use specified JRE", project);
    }
    return null;
  }

  private static void copyLogParameters(ApplicationConfiguration applicationConfiguration, MavenRunConfiguration mavenRunConfiguration) {
    for (PredefinedLogFile file: applicationConfiguration.getPredefinedLogFiles()) {
      mavenRunConfiguration.addPredefinedLogFile(file);
    }

    for (LogFileOptions op: applicationConfiguration.getLogFiles()) {
      mavenRunConfiguration.addLogFile(op.getPathPattern(), op.getName(), op.isEnabled(), op.isSkipContent(), op.isShowAll());
    }

    mavenRunConfiguration.setFileOutputPath(applicationConfiguration.getOutputFilePath());
    mavenRunConfiguration.setSaveOutputToFile(applicationConfiguration.isSaveOutputToFile());

    mavenRunConfiguration.setShowConsoleOnStdOut(applicationConfiguration.isShowConsoleOnStdOut());
    mavenRunConfiguration.setShowConsoleOnStdErr(applicationConfiguration.isShowConsoleOnStdErr());
  }

  public static List<String> patchVmParameters(ParametersList vmParameters) {
    List<String> patchedVmParameters = new ArrayList<>(vmParameters.getList());
    for (Iterator<String> iterator = patchedVmParameters.iterator(); iterator.hasNext(); ) {
      String parameter = iterator.next();
      if (parameter.contains("suspend=n,server=y")) {
        iterator.remove();
        patchedVmParameters.add(StringUtil.replace(parameter, "suspend=n,server=y", "suspend=y,server=y"));
        break;
      }
    }
    return patchedVmParameters;
  }

  private static class MyExecRunConfiguration extends MavenRunConfiguration {

    private final ApplicationConfiguration myApplicationConfiguration;

    public MyExecRunConfiguration(Project project, ConfigurationFactory configurationFactory,
                                  ApplicationConfiguration applicationConfiguration) {
      super(project, configurationFactory, applicationConfiguration.getName());
      myApplicationConfiguration = applicationConfiguration;
    }

    @NotNull
    @Override
    public RemoteConnectionCreator createRemoteConnectionCreator(JavaParameters javaParameters) {
      return new RemoteConnectionCreator() {

        @Override
        public RemoteConnection createRemoteConnection(ExecutionEnvironment environment) {
          try {
            JavaParameters parameters = new JavaParameters();
            parameters.setJdk(JavaParametersUtil.createProjectJdk(getProject(), myApplicationConfiguration.getAlternativeJrePath()));
            RemoteConnection connection = DebuggerManagerImpl.createDebugParameters(
              parameters, false, DebuggerSettings.getInstance().DEBUGGER_TRANSPORT, "", false);

            ParametersList programParametersList = javaParameters.getProgramParametersList();

            String execArgsPrefix = "-Dexec.args=";
            int execArgsIndex = indexOf(programParametersList.getList(), (Condition<String>)s -> s.startsWith(execArgsPrefix));
            String execArgsStr = programParametersList.get(execArgsIndex);

            ParametersList execArgs = new ParametersList();
            execArgs.addAll(patchVmParameters(parameters.getVMParametersList()));

            execArgs.addParametersString(execArgsStr.substring(execArgsPrefix.length()));

            String classPath = toSystemDependentName(parameters.getClassPath().getPathsString());
            execArgs.replaceOrPrepend("%classpath", "%classpath" + File.pathSeparator + classPath);

            programParametersList.set(execArgsIndex, execArgsPrefix + execArgs.getParametersString());
            return connection;
          }
          catch (ExecutionException e) {
            throw new RuntimeException("Cannot create debug connection", e);
          }
        }

        @Override
        public boolean isPollConnection() {
          return true;
        }
      };
    }
  }
}
