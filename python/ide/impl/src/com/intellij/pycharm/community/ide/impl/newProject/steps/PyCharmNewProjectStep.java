// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.newProject.steps;

import com.intellij.ide.util.projectWizard.AbstractNewProjectStep;
import com.intellij.ide.util.projectWizard.ProjectSettingsStepBase;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.impl.welcomeScreen.collapsedActionGroup.CollapsedActionGroup;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.pycharm.community.ide.impl.PyCharmCommunityCustomizationBundle;
import com.intellij.util.ObjectUtils;
import com.jetbrains.python.newProject.*;
import com.jetbrains.python.newProject.steps.PythonProjectSpecificSettingsStep;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

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
      var npwGenerator = ObjectUtils.tryCast(projectGenerator, NewProjectWizardDirectoryGeneratorAdapter.class);
      if (npwGenerator != null) {
        //noinspection unchecked
        return new NewProjectWizardProjectSettingsStep<PyNewProjectSettings>(npwGenerator);
      }
      else {
        return new PythonProjectSpecificSettingsStep<>(projectGenerator, callback);
      }
    }

    @Override
    public AnAction[] getActions(@NotNull List<? extends DirectoryProjectGenerator<?>> generators,
                                 @NotNull AbstractCallback<PyNewProjectSettings> callback) {
      generators = new ArrayList<>(generators);
      generators.sort(Comparator.comparing(DirectoryProjectGenerator::getName));
      generators.sort(Comparator.comparingInt(value -> {
        if (value instanceof PyFrameworkProjectGenerator) {
          return -2;
        }
        if (value instanceof PythonProjectGenerator) {
          return -1;
        }
        return 0;
      }));

      //noinspection unchecked
      var map = StreamEx.of(generators)
        .map(generator -> new Pair<>(generator, getActions((DirectoryProjectGenerator<PyNewProjectSettings>)generator, callback)))
        .partitioningBy((pair) -> pair.first instanceof PythonProjectGenerator);

      var python = new DefaultActionGroup(PyCharmCommunityCustomizationBundle.message("new.project.python.group.name"),
                                          map.get(true).stream().flatMap(pair -> Arrays.stream(pair.second)).toList());
      var other = new CollapsedActionGroup(PyCharmCommunityCustomizationBundle.message("new.project.other.group.name"),
                                           map.get(false).stream().flatMap(pair -> Arrays.stream(pair.second)).toList());
      return new AnAction[]{python, other};
    }
  }
}
