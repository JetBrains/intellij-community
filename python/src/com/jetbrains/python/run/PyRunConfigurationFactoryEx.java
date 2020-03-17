// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.module.Module;

public abstract class PyRunConfigurationFactoryEx extends PyRunConfigurationFactory {
  public abstract RunnerAndConfigurationSettings createRunConfiguration(Module module, ConfigurationFactory factory);
}
