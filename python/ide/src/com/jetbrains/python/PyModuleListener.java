/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python;

import com.intellij.ProjectTopics;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ModuleAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBus;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author yole
 */
public class PyModuleListener {
  public PyModuleListener(MessageBus messageBus) {
    messageBus.connect().subscribe(ProjectTopics.MODULES, new ModuleAdapter() {
      @Override
      public void beforeModuleRemoved(@NotNull Project project, @NotNull Module module) {
        final RunManagerEx runManager = RunManagerEx.getInstanceEx(project);
        final Collection<RunnerAndConfigurationSettings> configurations = new ArrayList<>(runManager.getSortedConfigurations());
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
