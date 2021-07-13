// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package de.plushnikov.intellij.plugin.jps;

import com.intellij.compiler.server.BuildManager;
import com.intellij.compiler.server.BuildProcessParametersProvider;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import de.plushnikov.intellij.plugin.Version;
import de.plushnikov.intellij.plugin.settings.ProjectSettings;
import de.plushnikov.intellij.plugin.util.LombokLibraryUtil;
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
        Version.isLessThan(lombokVersion, Version.LAST_LOMBOK_VERSION_WITH_JPS_FIX)) {
      result.add("-Djps.track.ap.dependencies=false");
    }

    if (ProjectSettings.isEnabled(myProject, ProjectSettings.IS_LOMBOK_ADD_OPENS_FIX_ENABLED) &&
        BuildManager.getBuildProcessRuntimeSdk(myProject).getSecond().isAtLeast(JavaSdkVersion.JDK_16) &&
        Version.isLessThan(lombokVersion, Version.LOMBOK_VERSION_WITH_JDK16_FIX)) {
      result.add("--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED");
      result.add("--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED");
      result.add("--add-opens=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED");
      result.add("--add-opens=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED");
      result.add("--add-opens=jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED");
      result.add("--add-opens=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED");
      result.add("--add-opens=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED");
      result.add("--add-opens=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED");
      result.add("--add-opens=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED");
      result.add("--add-opens=jdk.compiler/com.sun.tools.javac.jvm=ALL-UNNAMED");
    }

    return result;
  }
}
