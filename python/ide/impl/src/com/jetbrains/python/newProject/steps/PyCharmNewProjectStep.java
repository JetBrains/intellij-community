// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.newProject.steps;

import com.intellij.ide.util.projectWizard.AbstractNewProjectStep;
import com.intellij.ide.util.projectWizard.ProjectSettingsStepBase;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.platform.DirectoryProjectGenerator;
import com.jetbrains.python.newProject.PyFrameworkProjectGenerator;
import com.jetbrains.python.newProject.PyNewProjectSettings;
import com.jetbrains.python.newProject.PythonProjectGenerator;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Comparator;

public final class PyCharmNewProjectStep extends AbstractNewProjectStep<PyNewProjectSettings> {
  public PyCharmNewProjectStep() {
    super(new Customization());
  }

  protected static class Customization extends AbstractNewProjectStep.Customization<PyNewProjectSettings> {
    @NotNull
    @Override
    protected AbstractCallback<PyNewProjectSettings> createCallback() {
      return new PythonGenerateProjectCallback<>();
    }

    @NotNull
    @Override
    protected DirectoryProjectGenerator<PyNewProjectSettings> createEmptyProjectGenerator() {
      return new PythonBaseProjectGenerator();
    }

    @NotNull
    @Override
    protected ProjectSettingsStepBase<PyNewProjectSettings> createProjectSpecificSettingsStep(@NotNull DirectoryProjectGenerator<PyNewProjectSettings> projectGenerator,
                                                                                              @NotNull AbstractCallback<PyNewProjectSettings> callback) {
      return new ProjectSpecificSettingsStep<>(projectGenerator, callback);
    }

    @Override
    public AnAction[] getActions(DirectoryProjectGenerator<PyNewProjectSettings> @NotNull [] generators,
                                 @NotNull AbstractCallback<PyNewProjectSettings> callback) {
      Arrays.sort(generators, Comparator.comparing(DirectoryProjectGenerator::getName));
      Arrays.sort(generators, Comparator.comparingInt(value -> {
        if (value instanceof PyFrameworkProjectGenerator) {
          return -2;
        }
        if (value instanceof PythonProjectGenerator) {
          return -1;
        }
        return 0;
      }));
      return StreamEx.of(generators)
        .flatMap(generator -> StreamEx.of(getActions(generator, callback)))
        .toArray(EMPTY_ARRAY);
    }
  }
}
