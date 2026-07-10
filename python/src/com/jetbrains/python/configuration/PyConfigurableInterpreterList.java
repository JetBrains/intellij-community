// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.configuration;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.jetbrains.python.sdk.ProjectExtKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @deprecated The cached SDK model was removed; query the live SDK table via
 * {@link ProjectExtKt#getAssignablePythonSdks(Project, com.intellij.openapi.module.Module)} instead. Retained for
 * external plugins that still reference this service.
 */
@Deprecated
public final class PyConfigurableInterpreterList {
  private final @NotNull Project myProject;

  private PyConfigurableInterpreterList(@NotNull Project project) {
    myProject = project;
  }

  /**
   * @deprecated Obtain interpreters via {@link ProjectExtKt#getAssignablePythonSdks(Project, com.intellij.openapi.module.Module)}.
   */
  @Deprecated
  public static PyConfigurableInterpreterList getInstance(@Nullable Project project) {
    final Project effectiveProject = project != null ? project : ProjectManager.getInstance().getDefaultProject();
    return new PyConfigurableInterpreterList(effectiveProject);
  }

  /**
   * @deprecated Use {@link ProjectExtKt#getAssignablePythonSdks(Project, com.intellij.openapi.module.Module)} with a {@code null} module.
   */
  @Deprecated
  public @NotNull List<Sdk> getAllPythonSdks() {
    return ProjectExtKt.getAssignablePythonSdks(myProject, null);
  }
}
