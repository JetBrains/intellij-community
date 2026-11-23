// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.plugin.java.facet;

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.SettingsStep;
import com.intellij.ide.util.projectWizard.SourcePathsBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.configuration.interpreter.PyInterpreterComboBox;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class PythonModuleBuilder extends PythonModuleBuilderBase implements SourcePathsBuilder {
  private List<Pair<String, String>> mySourcePaths;

  @Override
  public List<Pair<String, String>> getSourcePaths() {
    return mySourcePaths;
  }

  @Override
  public void setSourcePaths(final List<Pair<String, String>> sourcePaths) {
    mySourcePaths = sourcePaths;
  }

  @Override
  public void addSourcePath(final Pair<String, String> sourcePathInfo) {
    if (mySourcePaths == null) {
      mySourcePaths = new ArrayList<>();
    }
    mySourcePaths.add(sourcePathInfo);
  }

  @Override
  public boolean isAvailable() {
    return false;
  }

  /**
   * Adds the interpreter of the new module to the settings step. The module does not exist yet, so the combo lists the
   * interpreters of all Python projects. A new project has no Python project, so it gets no combo.
   */
  @Override
  public @Nullable ModuleWizardStep modifyProjectTypeStep(@NotNull SettingsStep settingsStep) {
    Project project = settingsStep.getContext().getProject();
    if (project == null) return null;
    PyInterpreterComboBox combo = new PyInterpreterComboBox(project, () -> null);
    combo.addSelectionListener(() -> {
      setInterpreter(combo.getSelectedInterpreter());
      return Unit.INSTANCE;
    });
    settingsStep.addSettingsField(PyBundle.message("configurable.PyWorkspaceStructureConfigurable.sdk.label"), combo.getComponent());
    return null;
  }
}
