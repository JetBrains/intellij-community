// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils

import org.jetbrains.idea.maven.utils.MavenUtil
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.openapi.project.Project
import java.lang.IllegalArgumentException

object MavenWslUtl : MavenUtil() {
  @JvmStatic
  fun getPropertiesFromMavenOpts(distribution: WSLDistribution): Map<String, String> {
    return parseMavenProperties(distribution.getEnvironmentVariable("MAVEN_OPTS"))
  }

  @JvmStatic
  fun getWslDistribution(project: Project): WSLDistribution {
    val basePath = project.basePath ?: throw IllegalArgumentException("Project $project with null base path")
    return WslDistributionManager.getInstance().distributionFromPath(basePath)
           ?: throw IllegalArgumentException("Distribution for path $basePath not found, check your WSL installation")
  }

}