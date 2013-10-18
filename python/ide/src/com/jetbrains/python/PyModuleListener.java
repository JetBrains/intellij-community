package com.jetbrains.python;

import com.intellij.ProjectTopics;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ModuleAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBus;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author yole
 */
public class PyModuleListener {
  public PyModuleListener(MessageBus messageBus) {
    messageBus.connect().subscribe(ProjectTopics.MODULES, new ModuleAdapter() {
      @Override
      public void beforeModuleRemoved(Project project, Module module) {
        final RunManagerEx runManager = RunManagerEx.getInstanceEx(project);
        final Collection<RunnerAndConfigurationSettings> configurations = new ArrayList<RunnerAndConfigurationSettings>(runManager.getSortedConfigurations());
        for (RunnerAndConfigurationSettings configuration : configurations) {
          if (configuration.getConfiguration() instanceof AbstractPythonRunConfiguration) {
            final Module configModule = ((AbstractPythonRunConfiguration)configuration.getConfiguration()).getModule();
            if (configModule == module) {
              runManager.removeConfiguration(configuration);
            }
          }
        }
      }
    });    
  }
}
