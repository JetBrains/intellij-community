// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText.run;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.run.PythonConfigurationFactoryBase;
import com.intellij.python.reStructuredText.RestBundle;
import com.intellij.python.reStructuredText.RestFileType;
import com.intellij.python.reStructuredText.run.docutils.DocutilsRunConfiguration;
import com.intellij.python.reStructuredText.run.sphinx.SphinxRunConfiguration;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
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
    private final @Nls String myName;
    private final String myId;

    RestConfigurationFactory(@NotNull final ConfigurationType type, @NotNull @Nls String name, @NotNull @NonNls String id) {
      super(type);
      myName = name;
      myId = id;
    }

    @NotNull
    @Override
    public String getName() {
      return myName;
    }

    @Override
    public @NotNull String getId() {
      return myId;
    }
  }

  private static class DocutilsRunConfigurationFactory extends RestConfigurationFactory {
    protected DocutilsRunConfigurationFactory(ConfigurationType type) {
      super(type, RestBundle.message("runcfg.docutils.docutils.task"), "Docutils task");
    }

    @Override
    @NotNull
    public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
      return new DocutilsRunConfiguration(project, this);
    }
  }

  private static class SphinxRunConfigurationFactory extends RestConfigurationFactory {
    protected SphinxRunConfigurationFactory(ConfigurationType type) {
      super(type, RestBundle.message("runcfg.docutils.sphinx.task"), "Sphinx task");
    }

    @Override
    @NotNull
    public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
      return new SphinxRunConfiguration(project, this);
    }
  }
}
