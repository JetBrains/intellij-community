/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.testing.tox;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.ArrayUtil;
import com.intellij.util.xmlb.SkipEmptySerializationFilter;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.Tag;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import com.jetbrains.serialization.AnnotationSerializationFilter;
import com.jetbrains.serialization.CompoundFilter;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Ilya.Kazakevich
 */
public final class PyToxConfiguration extends AbstractPythonRunConfiguration<PyToxConfiguration> {

  @NotNull
  private final Project myProject;

  @Tag
  @Nullable
  private String[] myArguments;
  @Tag
  @Nullable
  private String[] myRunOnlyEnvs;

  PyToxConfiguration(@NotNull final PyToxConfigurationFactory factory, @NotNull final Project project) {
    super(project, factory);
    myProject = project;
    // Module will be stored with XmlSerializer
    //noinspection AssignmentToSuperclassField
    mySkipModuleSerialization = true;
  }

  @NotNull
  String[] getRunOnlyEnvs() {
    return (myRunOnlyEnvs == null ? ArrayUtil.EMPTY_STRING_ARRAY : myRunOnlyEnvs.clone());
  }

  void setRunOnlyEnvs(@NotNull final String... tests) {
    myRunOnlyEnvs = tests.clone();
  }

  @NotNull
  String[] getArguments() {
    return (myArguments == null ? ArrayUtil.EMPTY_STRING_ARRAY : myArguments.clone());
  }

  void setArguments(@NotNull final String... arguments) {
    myArguments = arguments.clone();
  }

  @Override
  public void readExternal(final Element element) throws InvalidDataException {
    super.readExternal(element);
    XmlSerializer.deserializeInto(this, element);
  }

  @Override
  public void writeExternal(final Element element) throws WriteExternalException {
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

  @Nullable
  @Override
  public RunProfileState getState(@NotNull final Executor executor, @NotNull final ExecutionEnvironment environment) {
    return new PyToxCommandLineState(this, environment);
  }
}
