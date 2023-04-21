// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package de.plushnikov.intellij.plugin.jps;

import com.intellij.compiler.server.BuildProcessParametersProvider;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LombokVersion;
import de.plushnikov.intellij.plugin.settings.ProjectSettings;
import com.intellij.openapi.roots.LombokLibraryUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LombokBuildProcessParametersProvider extends BuildProcessParametersProvider {

  private final Project myProject;

  public LombokBuildProcessParametersProvider(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public @NotNull List<String> getVMArguments() {
    if(!ReadAction.nonBlocking(() -> LombokLibraryUtil.hasLombokLibrary(myProject)).executeSynchronously()) {
      return Collections.emptyList();
    }

    List<String> result = new ArrayList<>();

    final String lombokVersion = LombokLibraryUtil.getLombokVersionCached(myProject);
    if (ProjectSettings.isEnabled(myProject, ProjectSettings.IS_LOMBOK_JPS_FIX_ENABLED) &&
        LombokVersion.isLessThan(lombokVersion, LombokVersion.LAST_LOMBOK_VERSION_WITH_JPS_FIX)) {
      result.add("-Djps.track.ap.dependencies=false");
    }

    return result;
  }
}
