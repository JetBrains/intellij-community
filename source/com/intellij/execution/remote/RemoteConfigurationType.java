/*
 * Class RemoteConfigurationFactory
 * @author Jeka
 */
package com.intellij.execution.remote;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

public class RemoteConfigurationType implements ConfigurationType {
  private final ConfigurationFactory myFactory;
  private static final Icon ICON = IconLoader.getIcon("/runConfigurations/remote.png");

  /**reflection*/
  public RemoteConfigurationType() {
    myFactory = new ConfigurationFactory(this) {
      public RunConfiguration createTemplateConfiguration(Project project) {
        return new RemoteConfiguration("", project, this);
      }

    };
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  public String getDisplayName() {
    return "Remote";
  }

  public String getConfigurationTypeDescription() {
    return "Remote debug configuration";
  }

  public Icon getIcon() {
    return ICON;
  }

  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[]{myFactory};
  }

  public String getComponentName() {
    return "Remote";
  }

  public static RemoteConfigurationType getInstance() {
    return ApplicationManager.getApplication().getComponent(RemoteConfigurationType.class);
  }

}