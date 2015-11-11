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
package com.jetbrains.python.newProject.actions;

import com.google.common.collect.Lists;
import com.intellij.ide.util.projectWizard.AbstractNewProjectStep;
import com.intellij.ide.util.projectWizard.ProjectSettingsStepBase;
import com.intellij.ide.util.projectWizard.actions.ProjectSpecificAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.ui.Messages;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.util.BooleanFunction;
import com.intellij.util.NullableConsumer;
import com.jetbrains.python.newProject.PyFrameworkProjectGenerator;
import com.jetbrains.python.newProject.PythonBaseProjectGenerator;
import com.jetbrains.python.newProject.PythonProjectGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class PyCharmNewProjectStep extends AbstractNewProjectStep {

  public PyCharmNewProjectStep() {
    super(new Customization());
  }

  private static class Customization extends AbstractNewProjectStep.Customization {
    private final List<DirectoryProjectGenerator> pluginSpecificGenerators = Lists.newArrayList();

    @NotNull
    @Override
    protected NullableConsumer<ProjectSettingsStepBase> createCallback() {
      return new GenerateProjectCallback();
    }

    @NotNull
    @Override
    protected DirectoryProjectGenerator createEmptyProjectGenerator() {
      return new PythonBaseProjectGenerator();
    }

    @NotNull
    @Override
    protected ProjectSettingsStepBase createProjectSpecificSettingsStep(@NotNull DirectoryProjectGenerator emptyProjectGenerator,
                                                                        @NotNull NullableConsumer<ProjectSettingsStepBase> callback) {
      return new ProjectSpecificSettingsStep(emptyProjectGenerator, callback);
    }

    @NotNull
    @Override
    protected DirectoryProjectGenerator[] getProjectGenerators() {
      DirectoryProjectGenerator[] generators = super.getProjectGenerators();

      Arrays.sort(generators, new Comparator<DirectoryProjectGenerator>() {
        @Override
        public int compare(DirectoryProjectGenerator o1, DirectoryProjectGenerator o2) {
          if (o1 instanceof PyFrameworkProjectGenerator && !(o2 instanceof PyFrameworkProjectGenerator)) return -1;
          if (!(o1 instanceof PyFrameworkProjectGenerator) && o2 instanceof PyFrameworkProjectGenerator) return 1;
          return o1.getName().compareTo(o2.getName());
        }
      });
      return generators;
    }

    @Override
    public void setUpBasicAction(@NotNull ProjectSpecificAction projectSpecificAction, @NotNull DirectoryProjectGenerator[] generators) {
      if (generators.length == 0) {
        projectSpecificAction.setPopup(false);
      }
    }

    @NotNull
    @Override
    public AnAction[] getActions(@NotNull DirectoryProjectGenerator generator, @NotNull NullableConsumer<ProjectSettingsStepBase> callback) {
      if (generator instanceof PythonProjectGenerator) {
        ProjectSpecificAction group =
          new ProjectSpecificAction(generator, new ProjectSpecificSettingsStep(generator, new PythonGenerateProjectCallback()));
        return group.getChildren(null);
      }
      else {
        pluginSpecificGenerators.add(generator);
        return AnAction.EMPTY_ARRAY;
      }
    }

    @NotNull
    @Override
    public AnAction[] getExtraActions(@NotNull NullableConsumer<ProjectSettingsStepBase> callback) {
      if (!pluginSpecificGenerators.isEmpty()) {
        PluginSpecificProjectsStep step = new PluginSpecificProjectsStep(callback, pluginSpecificGenerators);
        return step.getChildren(null);
      }
      return AnAction.EMPTY_ARRAY;
    }
  }

  private static class PythonGenerateProjectCallback implements NullableConsumer<ProjectSettingsStepBase> {
    @Override
    public void consume(@Nullable ProjectSettingsStepBase base) {
      if (base == null) return;
      final DirectoryProjectGenerator generator = base.getProjectGenerator();
      final NullableConsumer<ProjectSettingsStepBase> callback = new GenerateProjectCallback();
      if (generator instanceof PythonProjectGenerator && base instanceof ProjectSpecificSettingsStep) {
        final BooleanFunction<PythonProjectGenerator> beforeProjectGenerated = ((PythonProjectGenerator)generator).
          beforeProjectGenerated(((ProjectSpecificSettingsStep)base).getSdk());
        if (beforeProjectGenerated != null) {
          final boolean result = beforeProjectGenerated.fun((PythonProjectGenerator)generator);
          if (result) {
            callback.consume(base);
          }
          else {
            Messages.showWarningDialog("Project can not be generated", "Error in Project Generation");
          }
        }
        else {
          callback.consume(base);
        }
      }
    }
  }
}
