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
package com.jetbrains.rest.run;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.run.PythonConfigurationFactoryBase;
import com.jetbrains.rest.RestBundle;
import com.jetbrains.rest.RestFileType;
import com.jetbrains.rest.run.docutils.DocutilsRunConfiguration;
import com.jetbrains.rest.run.sphinx.SphinxRunConfiguration;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * User : catherine
 */
public class RestRunConfigurationType implements ConfigurationType {
  public final ConfigurationFactory DOCUTILS_FACTORY = new DocutilsRunConfigurationFactory(this);
  public final ConfigurationFactory SPHINX_FACTORY = new SphinxRunConfigurationFactory(this);

  private String myId = "docs";

  public String getDisplayName() {
    return RestBundle.message("runcfg.docutils.display_name");
  }

  public static RestRunConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(RestRunConfigurationType.class);
  }

  public String getConfigurationTypeDescription() {
    return RestBundle.message("runcfg.docutils.description");
  }

  public Icon getIcon() {
    return RestFileType.INSTANCE.getIcon();
  }

  @NotNull
  public String getId() {
    return myId;
  }

  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[] {DOCUTILS_FACTORY, SPHINX_FACTORY};
  }

  private static abstract class RestConfigurationFactory extends PythonConfigurationFactoryBase {
    private final String myName;

    public RestConfigurationFactory(@NotNull final ConfigurationType type, @NotNull String name) {
      super(type);
      myName = name;
    }

    public String getName() {
      return myName;
    }
  }

  private static class DocutilsRunConfigurationFactory extends RestConfigurationFactory {
    protected DocutilsRunConfigurationFactory(ConfigurationType type) {
      super(type, "Docutils task");
    }

    public RunConfiguration createTemplateConfiguration(Project project) {
      return new DocutilsRunConfiguration(project, this);
    }
  }

  private static class SphinxRunConfigurationFactory extends RestConfigurationFactory {
    protected SphinxRunConfigurationFactory(ConfigurationType type) {
      super(type, "Sphinx task");
    }

    public RunConfiguration createTemplateConfiguration(Project project) {
      return new SphinxRunConfiguration(project, this);
    }
  }
}
