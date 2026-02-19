// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  public PyToxConfigurationFactory(final @NotNull ConfigurationType type) {
    super(type);
  }

  @Override
  public @NotNull String getName() {
    return "Tox"; //NON-NLS
  }

  @Override
  public @NotNull String getId() {
    return "Tox";
  }

  @Override
  public @NotNull RunConfiguration createTemplateConfiguration(final @NotNull Project project) {
    return new PyToxConfiguration(this, project);
  }
}
