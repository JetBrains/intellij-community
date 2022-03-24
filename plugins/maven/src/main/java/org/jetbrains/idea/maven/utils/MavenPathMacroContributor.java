// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.openapi.application.PathMacroContributor;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

final class MavenPathMacroContributor implements PathMacroContributor {
  @Override
  public void registerPathMacros(@NotNull Map<String, String> macros, @NotNull Map<String, String> legacyMacros) {
    String repository = MavenUtil.resolveLocalRepository(null, null, null).getAbsolutePath();
    macros.put(PathMacrosImpl.MAVEN_REPOSITORY, repository);
  }

  @Override
  public void forceRegisterPathMacros(@NotNull Map<String, String> macros) {
    if (System.getProperty(MavenUtil.PROP_FORCED_M2_HOME) != null) {
      macros.put(PathMacrosImpl.MAVEN_REPOSITORY, System.getProperty(MavenUtil.PROP_FORCED_M2_HOME));
    }
  }
}
