// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
public final class RestRunConfigurationType implements ConfigurationType {
  public final ConfigurationFactory DOCUTILS_FACTORY = new DocutilsRunConfigurationFactory(this);
  public final ConfigurationFactory SPHINX_FACTORY = new SphinxRunConfigurationFactory(this);

  @NotNull
  @Override
  public String getDisplayName() {
    return RestBundle.message("runcfg.docutils.display_name");
  }

  public static RestRunConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(RestRunConfigurationType.class);
  }

  @Override
  public String getConfigurationTypeDescription() {
    return RestBundle.message("runcfg.docutils.description");
  }

  @Override
  public Icon getIcon() {
    return RestFileType.INSTANCE.getIcon();
  }

  @Override
  @NotNull
  public String getId() {
    String id = "docs";
    return id;
  }

  @Override
  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[] {DOCUTILS_FACTORY, SPHINX_FACTORY};
  }

  @Override
  public String getHelpTopic() {
    return "reference.dialogs.rundebug.docs";
  }

  @Override
  public boolean isDumbAware() {
    return true;
  }

  private static abstract class RestConfigurationFactory extends PythonConfigurationFactoryBase {
    private final String myName;

    RestConfigurationFactory(@NotNull final ConfigurationType type, @NotNull String name) {
      super(type);
      myName = name;
    }

    @NotNull
    @Override
    public String getName() {
      return myName;
    }
  }

  private static class DocutilsRunConfigurationFactory extends RestConfigurationFactory {
    protected DocutilsRunConfigurationFactory(ConfigurationType type) {
      super(type, "Docutils task");
    }

    @Override
    @NotNull
    public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
      return new DocutilsRunConfiguration(project, this);
    }
  }

  private static class SphinxRunConfigurationFactory extends RestConfigurationFactory {
    protected SphinxRunConfigurationFactory(ConfigurationType type) {
      super(type, "Sphinx task");
    }

    @Override
    @NotNull
    public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
      return new SphinxRunConfiguration(project, this);
    }
  }
}
