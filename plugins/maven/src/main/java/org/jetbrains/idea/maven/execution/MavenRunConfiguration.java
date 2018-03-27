/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.idea.maven.execution;

import com.intellij.diagnostic.logging.LogConfigurationPanel;
import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.SettingsEditorGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.*;

public class MavenRunConfiguration extends LocatableConfigurationBase implements ModuleRunProfile {
  private MavenSettings mySettings;

  protected MavenRunConfiguration(Project project, ConfigurationFactory factory, String name) {
    super(project, factory, name);
    mySettings = new MavenSettings(project);
  }

  @Override
  public MavenRunConfiguration clone() {
    MavenRunConfiguration clone = (MavenRunConfiguration)super.clone();
    clone.mySettings = mySettings.clone();
    return clone;
  }

  @NotNull
  @Override
  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    SettingsEditorGroup<MavenRunConfiguration> group = new SettingsEditorGroup<>();

    group.addEditor(RunnerBundle.message("maven.runner.parameters.title"), new MavenRunnerParametersSettingEditor(getProject()));

    group.addEditor(ProjectBundle.message("maven.tab.general"), new MavenGeneralSettingsEditor(getProject()));
    group.addEditor(RunnerBundle.message("maven.tab.runner"), new MavenRunnerSettingsEditor(getProject()));
    group.addEditor(ExecutionBundle.message("logs.tab.title"), new LogConfigurationPanel<>());
    return group;
  }

  public JavaParameters createJavaParameters(@Nullable Project project) throws ExecutionException {
    return MavenExternalParameters
      .createJavaParameters(project, mySettings.myRunnerParameters, mySettings.myGeneralSettings, mySettings.myRunnerSettings, this);
  }

  @Override
  public RunProfileState getState(@NotNull final Executor executor, @NotNull final ExecutionEnvironment env) throws ExecutionException {
    JavaCommandLineState state = new JavaCommandLineState(env) {
      @Override
      protected JavaParameters createJavaParameters() throws ExecutionException {
        return MavenRunConfiguration.this.createJavaParameters(env.getProject());
      }

      @NotNull
      @Override
      public ExecutionResult execute(@NotNull Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
        DefaultExecutionResult res = (DefaultExecutionResult)super.execute(executor, runner);
        if (executor.getId().equals(ToolWindowId.RUN)
            && MavenResumeAction.isApplicable(env.getProject(), getJavaParameters(), MavenRunConfiguration.this)) {
          MavenResumeAction resumeAction = new MavenResumeAction(res.getProcessHandler(), runner, env);
          res.setRestartActions(resumeAction);
        }
        return res;
      }

      @NotNull
      @Override
      protected OSProcessHandler startProcess() throws ExecutionException {
        OSProcessHandler result = super.startProcess();
        result.setShouldDestroyProcessRecursively(true);
        result.addProcessListener(new ProcessAdapter() {
          @Override
          public void processTerminated(@NotNull ProcessEvent event) {
            updateProjectsFolders();
          }
        });
        return result;
      }
    };
    state.setConsoleBuilder(MavenConsoleImpl.createConsoleBuilder(getProject()));
    return state;
  }

  private void updateProjectsFolders() {
    MavenProjectsManager.getInstance(getProject()).updateProjectTargetFolders();
  }

  @Nullable
  public MavenGeneralSettings getGeneralSettings() {
    return mySettings.myGeneralSettings;
  }

  public void setGeneralSettings(@Nullable MavenGeneralSettings settings) {
    mySettings.myGeneralSettings = settings;
  }

  @Nullable
  public MavenRunnerSettings getRunnerSettings() {
    return mySettings.myRunnerSettings;
  }

  public void setRunnerSettings(@Nullable MavenRunnerSettings settings) {
    mySettings.myRunnerSettings = settings;
  }

  public MavenRunnerParameters getRunnerParameters() {
    return mySettings.myRunnerParameters;
  }

  public void setRunnerParameters(MavenRunnerParameters p) {
    mySettings.myRunnerParameters = p;
  }

  @Override
  public void readExternal(@NotNull Element element) throws InvalidDataException {
    super.readExternal(element);

    Element mavenSettingsElement = element.getChild(MavenSettings.TAG);
    if (mavenSettingsElement != null) {
      mySettings = XmlSerializer.deserialize(mavenSettingsElement, MavenSettings.class);

      if (mySettings.myRunnerParameters == null) mySettings.myRunnerParameters = new MavenRunnerParameters();

      // fix old settings format
      mySettings.myRunnerParameters.fixAfterLoadingFromOldFormat();
    }
  }

  @Override
  public void writeExternal(@NotNull Element element) throws WriteExternalException {
    super.writeExternal(element);
    element.addContent(XmlSerializer.serialize(mySettings));
  }

  @Override
  public String suggestedName() {
    return MavenRunConfigurationType.generateName(getProject(), mySettings.myRunnerParameters);
  }

  public static class MavenSettings implements Cloneable {
    public static final String TAG = "MavenSettings";

    public MavenGeneralSettings myGeneralSettings;
    public MavenRunnerSettings myRunnerSettings;
    public MavenRunnerParameters myRunnerParameters;

    /* reflection only */
    public MavenSettings() {
    }

    public MavenSettings(Project project) {
      this(null, null, new MavenRunnerParameters());
    }

    private MavenSettings(@Nullable MavenGeneralSettings cs, @Nullable MavenRunnerSettings rs, MavenRunnerParameters rp) {
      myGeneralSettings = cs == null ? null : cs.clone();
      myRunnerSettings = rs == null ? null : rs.clone();
      myRunnerParameters = rp.clone();
    }

    @Override
    protected MavenSettings clone() {
      return new MavenSettings(myGeneralSettings, myRunnerSettings, myRunnerParameters);
    }
  }
}
