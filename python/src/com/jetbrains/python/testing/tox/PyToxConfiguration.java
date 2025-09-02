// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing.tox;

import com.intellij.execution.Executor;
import com.intellij.execution.Location;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.xmlb.SkipEmptySerializationFilter;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.Tag;
import com.jetbrains.python.serialization.AnnotationSerializationFilter;
import com.jetbrains.python.serialization.CompoundFilter;
import com.jetbrains.python.testing.AbstractPythonTestRunConfiguration;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.List;

/**
 * @author Ilya.Kazakevich
 */
public final class PyToxConfiguration extends AbstractPythonTestRunConfiguration<PyToxConfiguration> {

  private final @NotNull Project myProject;

  @Tag("arguments")
  private String @Nullable [] myArguments;
  @Tag("runOnlyEnvs")
  private String @Nullable [] myRunOnlyEnvs;

  PyToxConfiguration(final @NotNull PyToxConfigurationFactory factory, final @NotNull Project project) {
    super(project, factory, "tox");
    myProject = project;
    // Module will be stored with XmlSerializer
    //noinspection AssignmentToSuperclassField
    mySkipModuleSerialization = true;
  }

  @Override
  public boolean isIdTestBased() {
    return true;
  }

  String @NotNull [] getRunOnlyEnvs() {
    return (myRunOnlyEnvs == null ? ArrayUtilRt.EMPTY_STRING_ARRAY : myRunOnlyEnvs.clone());
  }

  public void setRunOnlyEnvs(final String @NotNull ... tests) {
    myRunOnlyEnvs = tests.clone();
  }

  String @NotNull [] getArguments() {
    return (myArguments == null ? ArrayUtilRt.EMPTY_STRING_ARRAY : myArguments.clone());
  }

  @VisibleForTesting
  public void setArguments(final String @NotNull ... arguments) {
    myArguments = arguments.clone();
  }

  @Override
  public void readExternal(final @NotNull Element element) throws InvalidDataException {
    super.readExternal(element);
    XmlSerializer.deserializeInto(this, element);
  }

  @Override
  public void writeExternal(final @NotNull Element element) throws WriteExternalException {
    super.writeExternal(element);
    XmlSerializer.serializeInto(this, element, new CompoundFilter(
      new SkipEmptySerializationFilter(),
      new AnnotationSerializationFilter()
    ));
  }

  @Override
  protected SettingsEditor<PyToxConfiguration> createConfigurationEditor() {
    return new PyToxConfigurationSettings(myProject);
  }

  @Override
  public @NotNull RunProfileState getState(final @NotNull Executor executor, final @NotNull ExecutionEnvironment environment) {
    return new PyToxCommandLineState(this, environment);
  }

  @Override
  public @Nullable String getTestSpec(final @NotNull Location<?> location, final @NotNull AbstractTestProxy failedTest) {
    AbstractTestProxy test = failedTest;
    while (test != null) {
      final String url = test.getLocationUrl();
      if (url != null) {
        final String protocol = VirtualFileManager.extractProtocol(url);
        if (PyToxTestLocator.PROTOCOL_ID.equals(protocol)) {
          return VirtualFileManager.extractPath(url);
        }
      }
      test = test.getParent();
    }
    return null;
  }

  @Override
  public void addTestSpecsAsParameters(final @NotNull ParamsGroup paramsGroup, final @NotNull List<String> testSpecs) {
    if (!testSpecs.isEmpty()) {
      paramsGroup.addParameter(String.format("-e %s", StringUtil.join(testSpecs, ",")));
    }
    if (myArguments != null) {
      paramsGroup.addParameters(myArguments);
    }
  }
}
