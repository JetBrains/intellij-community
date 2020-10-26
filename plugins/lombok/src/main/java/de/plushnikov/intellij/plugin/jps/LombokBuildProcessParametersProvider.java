// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package de.plushnikov.intellij.plugin.jps;

import com.intellij.compiler.server.BuildProcessParametersProvider;
import com.intellij.openapi.project.Project;
import de.plushnikov.intellij.plugin.activity.LombokProjectValidatorActivity;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public final class LombokBuildProcessParametersProvider extends BuildProcessParametersProvider {

  private final Project myProject;

  public LombokBuildProcessParametersProvider(Project project) {
    myProject = project;
  }

  @Override
  public @NotNull List<String> getVMArguments() {
    if (LombokProjectValidatorActivity.isVersionLessThan1_18_16(myProject)) {
      return Collections.singletonList("-Djps.track.ap.dependencies=false");
    }
    return super.getVMArguments();
  }
}
