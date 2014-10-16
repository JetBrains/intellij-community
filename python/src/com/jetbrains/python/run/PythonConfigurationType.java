/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.run;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import icons.PythonIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author yole
 */
public class PythonConfigurationType implements ConfigurationType {

  private final PythonConfigurationFactory myFactory = new PythonConfigurationFactory(this);

  public static PythonConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(PythonConfigurationType.class);
  }

  public static class PythonConfigurationFactory extends PythonConfigurationFactoryBase {
    protected PythonConfigurationFactory(ConfigurationType configurationType) {
      super(configurationType);
    }

    public RunConfiguration createTemplateConfiguration(Project project) {
      return new PythonRunConfiguration(project, this);
    }
  }

  public String getDisplayName() {
    return "Python";
  }

  public String getConfigurationTypeDescription() {
    return "Python run configuration";
  }

  public Icon getIcon() {
    return PythonIcons.Python.Python;
  }

  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[]{myFactory};
  }

  public PythonConfigurationFactory getFactory() {
    return myFactory;
  }

  @NotNull
  @NonNls
  public String getId() {
    return "PythonConfigurationType";
  }
}
