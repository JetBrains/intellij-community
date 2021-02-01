// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.server.WslMavenDistribution
import java.io.File
import java.io.IOException

object MavenWslUtil : MavenUtil() {
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

  @JvmStatic
  fun tryGetWslDistribution(project: Project): WSLDistribution? {
    return project.basePath?.let { WslDistributionManager.getInstance().distributionFromPath(it) }
  }

  @JvmStatic
  fun WSLDistribution.resolveUserSettingsFile(overriddenUserSettingsFile: String?): File {
    return if (!isEmptyOrSpaces(overriddenUserSettingsFile)) File(overriddenUserSettingsFile) else File(this.resolveM2Dir(), SETTINGS_XML)
  }

  /**
   * return file in WLS style
   */
  @JvmStatic
  fun WSLDistribution.resolveM2Dir(): File {
    return File(this.userHome, DOT_M2_DIR)
  }

  /**
   * return file in windows-style ("\\wsl$\distrib-name\home\user\.m2\settings.xml")
   */
  @JvmStatic
  fun WSLDistribution.resolveMavenHomeDirectory(overrideMavenHome: String?): File? {
    if (overrideMavenHome != null) {
      val home = this.getWindowsPath(overrideMavenHome)?.let(::File)
      return if (isValidMavenHome(home)) home else null
    }
    val m2home = this.environment[ENV_M2_HOME]
    if (m2home != null && !isEmptyOrSpaces(m2home)) {
      val homeFromEnv = this.getWindowsPath(m2home)?.let(::File)
      return if (isValidMavenHome(homeFromEnv)) homeFromEnv else null
    }
    var home = this.getWindowsPath("/usr/share/maven")?.let(::File)
    if (isValidMavenHome(home)) {
      return home
    }

    home = this.getWindowsPath("/usr/share/maven2")?.let(::File)
    if (isValidMavenHome(home)) {
      return home
    }
    return null
  }

  /**
   * return file in wsl style
   */
  @JvmStatic
  fun WSLDistribution.resolveLocalRepository(overriddenLocalRepository: String?,
                                             overriddenMavenHome: String?,
                                             overriddenUserSettingsFile: String?): File {
    var result: File? = null
    if (overriddenLocalRepository != null && !isEmptyOrSpaces(overriddenLocalRepository)) result = File(overriddenLocalRepository)
    if (result == null) {
      result = doResolveLocalRepository(this.resolveUserSettingsFile(overriddenUserSettingsFile),
                                        this.resolveGlobalSettingsFile(overriddenMavenHome))
    }
    return try {
      result.canonicalFile
    }
    catch (e: IOException) {
      result
    }
  }

  /**
   * return file in windows-style ("\\wsl$\distrib-name\home\user\.m2\settings.xml")
   */
  @JvmStatic
  fun WSLDistribution.resolveGlobalSettingsFile(overriddenMavenHome: String?): File? {
    val directory = this.resolveMavenHomeDirectory(overriddenMavenHome) ?: return null
    return File(File(directory, CONF_DIR), SETTINGS_XML)
  }

  @JvmStatic
  fun WSLDistribution.getDefaultMavenDistribution(overriddenMavenHome: String? = null): WslMavenDistribution? {
    val file = this.resolveMavenHomeDirectory(overriddenMavenHome) ?: return null
    val wslFile = this.getWslPath(file.path) ?: return null
    return WslMavenDistribution(this, wslFile, "default")
  }

  fun getJdkPath(wslDistribution: WSLDistribution): String? {
    return wslDistribution.getEnvironmentVariable("JAVA_HOME");
  }
}