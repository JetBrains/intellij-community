package com.intellij.execution.testframework.sm.runner;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Roman Chernyatchik
 */
public class MockRuntimeConfiguration extends RuntimeConfiguration {
  public MockRuntimeConfiguration(final Project project) {
    super("", project, new MockConfigurationFactory());
  }

  public void readExternal(Element element) throws InvalidDataException {
    //Do nothing
  }

  public void writeExternal(Element element) throws WriteExternalException {
    //Do nothing
  }

  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return null;
  }

  public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) throws ExecutionException {
    return null;
  }

  private static class MockConfigurationFactory extends ConfigurationFactory {
    public MockConfigurationFactory() {
      super(new MyConfigurationType());
    }

    @Override
      public RunConfiguration createTemplateConfiguration(Project project) {
      return new MockRuntimeConfiguration(project);
    }

  }

  private static class MyConfigurationType implements ConfigurationType {
    public String getDisplayName() {
      return "mock";
    }

    public String getConfigurationTypeDescription() {
      return "mock type";
    }

    public Icon getIcon() {
      return null;
    }

    @NotNull
      public String getId() {
      return "MockRuntimeConfiguration";
    }

    public ConfigurationFactory[] getConfigurationFactories() {
      return new ConfigurationFactory[0];
    }
  }
}
