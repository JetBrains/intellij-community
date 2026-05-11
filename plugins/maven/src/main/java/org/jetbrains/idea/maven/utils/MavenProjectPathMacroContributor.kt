// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils

import com.intellij.application.options.PathMacrosImpl
import com.intellij.openapi.components.impl.ProjectWidePathMacroContributor
import com.intellij.platform.eel.provider.EelProviderUtil
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.idea.maven.utils.MavenUtil.resolveDefaultLocalRepositoryForJpsMacros
import java.nio.file.Path
import java.util.Map

/**
 * Maven home path depends on an environment where the project is located.
 * On one hand, we have an application-wide macros in `path.macros.xml`. The data from these macros is inapplicable to non-local projects,
 * such as WSL and Docker based. Here we decide the location by project.
 */
internal class MavenProjectPathMacroContributor : ProjectWidePathMacroContributor {
  override fun getProjectPathMacros(projectFilePath: @SystemIndependent String): MutableMap<String, String> {
    return Map.of(PathMacrosImpl.MAVEN_REPOSITORY, getPathToDefaultMavenLocalRepositoryOnSpecificEnv(projectFilePath))
  }

  fun getPathToDefaultMavenLocalRepositoryOnSpecificEnv(projectFilePath: @SystemIndependent String): String {
    val projectFile = Path.of(projectFilePath)
    return resolveDefaultLocalRepositoryForJpsMacros(EelProviderUtil.getEelDescriptor(projectFile)).toAbsolutePath().toString()
  }
}
