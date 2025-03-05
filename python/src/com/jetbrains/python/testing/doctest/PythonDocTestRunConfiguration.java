// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing.doctest;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.target.TargetEnvironment;
import com.intellij.execution.target.value.TargetEnvironmentFunctions;
import com.intellij.execution.util.ProgramParametersConfigurator;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.testing.AbstractPythonLegacyTestRunConfiguration;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.intellij.execution.target.value.TargetEnvironmentFunctions.joinToStringFunction;
import static com.intellij.execution.target.value.TargetEnvironmentFunctions.targetPath;

public class PythonDocTestRunConfiguration extends AbstractPythonLegacyTestRunConfiguration<PythonDocTestRunConfiguration>
  implements PythonDocTestRunConfigurationParams {
  protected @NlsSafe String myPluralTitle = "Doctests";
  protected @NlsSafe String myTitle = "Doctest";

  private String myParameters = "";

  private boolean isParametersEnabled = true;

  public final @NotNull List<String> getParametersList() {
    return isParametersEnabled ? ProgramParametersConfigurator.expandMacrosAndParseParameters(myParameters) : new ArrayList<>();
  }

  public final @NotNull String getParametersString() {
    return isParametersEnabled ? myParameters : "";
  }

  public void addParameters(@NotNull String parameters) {
    myParameters = parameters;
  }

  public void setParametersEnabled(boolean isEnabled) {
    isParametersEnabled = isEnabled;
  }

  public boolean isParametersEnabled() {
    return isParametersEnabled;
  }

  public PythonDocTestRunConfiguration(Project project,
                                       ConfigurationFactory configurationFactory) {
    super(project, configurationFactory);
  }

  @Override
  protected SettingsEditor<PythonDocTestRunConfiguration> createConfigurationEditor() {
    return new PythonDocTestRunConfigurationEditor(getProject(), this);
  }

  @Override
  public RunProfileState getState(final @NotNull Executor executor, final @NotNull ExecutionEnvironment env) {
    return new PythonDocTestCommandLineState(this, env);
  }

  @Override
  protected String getTitle() {
    return myTitle;
  }

  @Override
  protected String getPluralTitle() {
    return myPluralTitle;
  }

  public static void copyParams(PythonDocTestRunConfigurationParams source, PythonDocTestRunConfigurationParams target) {
    copyParams(source.getTestRunConfigurationParams(), target.getTestRunConfigurationParams());
  }

  @Override
  @ApiStatus.Internal
  protected @Nullable Function<@NotNull TargetEnvironment, @NotNull @Nls String> createTargetEnvFunction(@NotNull VirtualFile virtualFile,
                                                                                                         @Nullable String className,
                                                                                                         @Nullable String funName) {
    String localPath = virtualFile.getCanonicalPath();
    if (localPath == null) return null;

    return joinToStringFunction(Stream.concat(
      Stream.of(targetPath(Path.of(localPath))),
      Stream.of(className, funName).filter(Objects::nonNull).map(TargetEnvironmentFunctions::constant)
    ).toList(), TEST_NAME_PARTS_SPLITTER);
  }
}
