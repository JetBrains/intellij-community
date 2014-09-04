/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.newProject.actions;

import com.google.common.collect.Lists;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.DumbAware;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.util.NullableConsumer;
import com.jetbrains.python.newProject.PyFrameworkProjectGenerator;
import com.jetbrains.python.newProject.PythonBaseProjectGenerator;
import com.jetbrains.python.newProject.PythonProjectGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class PyCharmNewProjectStep extends DefaultActionGroup implements DumbAware {

  public PyCharmNewProjectStep(@NotNull final String name, @Nullable final Runnable runnable) {
    this(name, runnable, false);
  }

  public PyCharmNewProjectStep(@NotNull final String name, @Nullable final Runnable runnable, boolean isWelcomeScreen) {
    super(name, true);

    final NullableConsumer<AbstractProjectSettingsStep> callback = new GenerateProjectCallback(runnable);

    final ProjectSpecificAction action = new ProjectSpecificAction(callback, new PythonBaseProjectGenerator(), isWelcomeScreen);
    add(action);

    final DirectoryProjectGenerator[] generators = Extensions.getExtensions(DirectoryProjectGenerator.EP_NAME);
    if (generators.length == 0) {
      action.setPopup(false);
    }
    Arrays.sort(generators, new Comparator<DirectoryProjectGenerator>() {
      @Override
      public int compare(DirectoryProjectGenerator o1, DirectoryProjectGenerator o2) {
        if (o1 instanceof PyFrameworkProjectGenerator && !(o2 instanceof PyFrameworkProjectGenerator)) return -1;
        if (!(o1 instanceof PyFrameworkProjectGenerator) && o2 instanceof PyFrameworkProjectGenerator) return 1;
        return o1.getName().compareTo(o2.getName());
      }
    });

    List<DirectoryProjectGenerator> pluginSpecificGenerators = Lists.newArrayList();
    for (DirectoryProjectGenerator generator : generators) {
      if (generator instanceof PythonProjectGenerator)
        add(new ProjectSpecificAction(callback, generator, isWelcomeScreen));
      else
        pluginSpecificGenerators.add(generator);
    }

    if (!pluginSpecificGenerators.isEmpty()) {
      add(new PluginSpecificProjectsStep(callback, pluginSpecificGenerators, isWelcomeScreen));
    }
  }

  public PyCharmNewProjectStep() {
    this("Select Project Type", null, true);
  }
}
