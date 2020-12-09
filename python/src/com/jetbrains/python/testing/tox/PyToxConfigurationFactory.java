// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing.tox;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.run.PythonConfigurationFactoryBase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Ilya.Kazakevich
 */
public final  class PyToxConfigurationFactory extends PythonConfigurationFactoryBase {
  public static final ConfigurationFactory INSTANCE = new PyToxConfigurationFactory(PyToxConfigurationType.INSTANCE);

  public PyToxConfigurationFactory(@NotNull final ConfigurationType type) {
    super(type);
  }

  @NotNull
  @Override
  public String getName() {
    return "Tox"; //NON-NLS
  }

  @Override
  public @NotNull String getId() {
    return "Tox";
  }

  @NotNull
  @Override
  public RunConfiguration createTemplateConfiguration(@NotNull final Project project) {
    return new PyToxConfiguration(this, project);
  }
}
