package com.jetbrains.rest.run;

import com.intellij.execution.configurations.*;
import com.intellij.openapi.project.Project;
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

  private static abstract class RestConfigurationFactory extends ConfigurationFactory {
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
