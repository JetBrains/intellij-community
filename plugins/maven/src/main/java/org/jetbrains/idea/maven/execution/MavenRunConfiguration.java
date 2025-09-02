// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.JavaRunConfigurationExtensionManager;
import com.intellij.execution.configurations.*;
import com.intellij.execution.impl.SingleConfigurationConfigurable;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.target.LanguageRuntimeType;
import com.intellij.execution.target.TargetEnvironmentAwareRunProfile;
import com.intellij.execution.target.TargetEnvironmentConfiguration;
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest;
import com.intellij.ide.DataManager;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.Transient;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.execution.run.MavenCommandLineState;
import org.jetbrains.idea.maven.execution.run.MavenExtRemoteConnectionCreator;
import org.jetbrains.idea.maven.execution.run.MavenShCommandLineState;
import org.jetbrains.idea.maven.execution.run.MavenTargetShCommandLineState;
import org.jetbrains.idea.maven.execution.run.configuration.MavenRunConfigurationSettingsEditor;
import org.jetbrains.idea.maven.execution.target.MavenRuntimeTargetConfiguration;
import org.jetbrains.idea.maven.execution.target.MavenRuntimeType;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.util.Objects;


public class MavenRunConfiguration extends LocatableConfigurationBase implements ModuleRunProfile, TargetEnvironmentAwareRunProfile {

  private @NotNull MavenSettings settings = new MavenSettings(getProject());

  protected MavenRunConfiguration(Project project, ConfigurationFactory factory, String name) {
    super(project, factory, name);
  }

  public @Nullable MavenGeneralSettings getGeneralSettings() {
    return settings.getGeneralSettings();
  }

  public void setGeneralSettings(@Nullable MavenGeneralSettings settings) {
    this.settings.setGeneralSettings(settings);
  }

  public @Nullable MavenRunnerSettings getRunnerSettings() {
    return settings.getRunnerSettings();
  }

  public void setRunnerSettings(@Nullable MavenRunnerSettings settings) {
    this.settings.setRunnerSettings(settings);
  }

  public @NotNull MavenRunnerParameters getRunnerParameters() {
    return settings.getRunnerParameters();
  }

  public void setRunnerParameters(@NotNull MavenRunnerParameters parameters) {
    settings.setRunnerParameters(parameters);
  }

  @Override
  public MavenRunConfiguration clone() {
    MavenRunConfiguration clone = (MavenRunConfiguration)super.clone();
    clone.settings = settings.clone();
    clone.initializeSettings();
    return clone;
  }

  private void initializeSettings() {
    if (StringUtil.isEmptyOrSpaces(settings.getRunnerParameters().getWorkingDirPath())) {
      String rootProjectPath = getRootProjectPath();
      if (rootProjectPath != null) {
        settings.getRunnerParameters().setWorkingDirPath(rootProjectPath);
      }
    }
  }

  private @Nullable String getRootProjectPath() {
    MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(getProject());
    MavenProject rootProject = ContainerUtil.getFirstItem(projectsManager.getRootProjects());
    return ObjectUtils.doIfNotNull(rootProject, it -> it.getDirectory());
  }

  @ApiStatus.Internal
  public JavaRunConfigurationExtensionManager getExtensionsManager() {
    return JavaRunConfigurationExtensionManager.getInstance();
  }

  @Override
  public @NotNull SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return LazyEditorFactory.create(this);
  }

  // MavenRunConfigurationSettingsEditor is a huge class, so we wrap its call here to not let bytecode verifier to load it eagerly from disk
  private static final class LazyEditorFactory {
    static @NotNull SettingsEditor<? extends RunConfiguration> create(@NotNull MavenRunConfiguration configuration) {
      return new MavenRunConfigurationSettingsEditor(configuration);
    }
  }

  @ApiStatus.Internal
  public static @Nullable String getTargetName(SettingsEditor<MavenRunConfiguration> mavenRunConfigurationSettingsEditor) {
    return DataManager.getInstance().getDataContext(mavenRunConfigurationSettingsEditor.getComponent())
      .getData(SingleConfigurationConfigurable.RUN_ON_TARGET_NAME_KEY);
  }

  public JavaParameters createJavaParameters(@NotNull Project project) throws ExecutionException {
    return MavenExternalParameters.createJavaParameters(project, getRunnerParameters(), getGeneralSettings(), getRunnerSettings(), this);
  }

  @Override
  public RunProfileState getState(final @NotNull Executor executor, final @NotNull ExecutionEnvironment env) {
    if (Registry.is("maven.use.scripts")) {
      if (env.getTargetEnvironmentRequest() instanceof LocalTargetEnvironmentRequest) {
        return new MavenShCommandLineState(env, this);
      }
      else {
        return new MavenTargetShCommandLineState(env, this);
      }
    }


    return new MavenCommandLineState(env, this);
  }

  public @NotNull RemoteConnectionCreator createRemoteConnectionCreator(JavaParameters javaParameters) {
    return new MavenExtRemoteConnectionCreator(javaParameters, this);
  }

  @Override
  public void readExternal(@NotNull Element element) throws InvalidDataException {
    super.readExternal(element);
    settings.readExternal(element);
    getExtensionsManager().readExternal(this, element);
  }

  @Override
  public void writeExternal(@NotNull Element element) throws WriteExternalException {
    super.writeExternal(element);
    settings.writeExternal(element);
    getExtensionsManager().writeExternal(this, element);
  }

  @Override
  public String suggestedName() {
    return MavenRunConfigurationType.generateName(getProject(), getRunnerParameters());
  }

  @Override
  public boolean canRunOn(@NotNull TargetEnvironmentConfiguration target) {
    return target.getRuntimes().findByType(MavenRuntimeTargetConfiguration.class) != null;
  }

  @Override
  public @Nullable LanguageRuntimeType<?> getDefaultLanguageRuntimeType() {
    return LanguageRuntimeType.EXTENSION_NAME.findExtension(MavenRuntimeType.class);
  }

  @Override
  public @Nullable String getDefaultTargetName() {
    return getOptions().getRemoteTarget();
  }

  @Override
  public void setDefaultTargetName(@Nullable String targetName) {
    getOptions().setRemoteTarget(targetName);
  }

  @Override
  public void onNewConfigurationCreated() {
    super.onNewConfigurationCreated();
    if (!getName().equals(suggestedName())) {
      // prevent RC name reset by RunConfigurable.installUpdateListeners on target change in UI
      getOptions().setNameGenerated(false);
    }
  }

  // TODO: make private
  @ApiStatus.Internal
  public static class MavenSettings implements Cloneable {
    public static final String TAG = "MavenSettings";

    public @Nullable MavenGeneralSettings myGeneralSettings;
    public @Nullable MavenRunnerSettings myRunnerSettings;
    public @Nullable MavenRunnerParameters myRunnerParameters;

    /* reflection only */
    public MavenSettings() {
    }

    public MavenSettings(Project project) {
      myRunnerParameters = new MavenRunnerParameters();
    }

    @Transient
    public @Nullable MavenGeneralSettings getGeneralSettings() {
      return myGeneralSettings;
    }

    public void setGeneralSettings(@Nullable MavenGeneralSettings generalSettings) {
      myGeneralSettings = generalSettings;
    }

    @Transient
    public @Nullable MavenRunnerSettings getRunnerSettings() {
      return myRunnerSettings;
    }

    public void setRunnerSettings(@Nullable MavenRunnerSettings runnerSettings) {
      myRunnerSettings = runnerSettings;
    }

    @Transient
    public @NotNull MavenRunnerParameters getRunnerParameters() {
      return Objects.requireNonNull(myRunnerParameters);
    }

    public void setRunnerParameters(@NotNull MavenRunnerParameters runnerParameters) {
      myRunnerParameters = runnerParameters;
    }

    @Override
    protected MavenSettings clone() {
      try {
        MavenSettings clone = (MavenSettings)super.clone();
        clone.myGeneralSettings = ObjectUtils.doIfNotNull(myGeneralSettings, MavenGeneralSettings::clone);
        clone.myRunnerSettings = ObjectUtils.doIfNotNull(myRunnerSettings, MavenRunnerSettings::clone);
        clone.myRunnerParameters = ObjectUtils.doIfNotNull(myRunnerParameters, MavenRunnerParameters::clone);
        return clone;
      }
      catch (CloneNotSupportedException e) {
        throw new Error(e);
      }
    }

    public void readExternal(@NotNull Element element) {
      Element mavenSettingsElement = element.getChild(TAG);
      if (mavenSettingsElement != null) {
        MavenSettings settings = XmlSerializer.deserialize(mavenSettingsElement, MavenSettings.class);
        if (settings.myRunnerParameters == null) {
          settings.myRunnerParameters = new MavenRunnerParameters();
        }

        // fix old settings format
        settings.myRunnerParameters.fixAfterLoadingFromOldFormat();

        myRunnerParameters = settings.myRunnerParameters;
        myGeneralSettings = settings.myGeneralSettings;
        myRunnerSettings = settings.myRunnerSettings;
      }
    }

    public void writeExternal(@NotNull Element element) throws WriteExternalException {
      element.addContent(XmlSerializer.serialize(this));
    }
  }
}
