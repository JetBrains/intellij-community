// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils;

import com.intellij.application.options.NamedPathMacroContributor;
import com.intellij.application.options.PathMacrosImpl;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Maven home path depends on an environment where the project is located.
 * On one hand, we have an application-wide macros in `path.macros.xml`. The data from these macros is inapplicable to non-local projects,
 * such as WSL and Docker based. Here we decide the location by project.
 */
final class MavenPathMacroContributor implements NamedPathMacroContributor {
  /**
   * The value stored in `path.macros.xml` overrides the registered one, so the settings of Maven are not read at a start that has it.
   */
  @Override
  public @NotNull Collection<String> getMacroNames() {
    return List.of(PathMacrosImpl.MAVEN_REPOSITORY);
  }

  @Override
  public void registerPathMacros(@NotNull Map<String, String> macros, @NotNull Map<String, String> legacyMacros) {
    String repository = MavenUtil.resolveDefaultLocalRepositoryForJpsMacros(null).toAbsolutePath().toString();
    macros.put(PathMacrosImpl.MAVEN_REPOSITORY, repository);
  }

  @Override
  public void forceRegisterPathMacros(@NotNull Map<String, String> macros) {
    if (System.getProperty(MavenUtil.MAVEN_REPO_LOCAL) != null) {
      macros.put(PathMacrosImpl.MAVEN_REPOSITORY, System.getProperty(MavenUtil.MAVEN_REPO_LOCAL));
    }
  }
}
