// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.idea.maven.server.WslMavenDistribution
import java.io.File

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

  /**
   * return file in windows-style ("\\wsl$\distrib-name\home\user\.m2\settings.xml")
   */
  @JvmStatic
  fun WSLDistribution.resolveUserSettingsFile(overriddenUserSettingsFile: String?): File {
    val localFile = if (!isEmptyOrSpaces(overriddenUserSettingsFile)) File(overriddenUserSettingsFile)
    else File(this.resolveM2Dir(), SETTINGS_XML)
    return localFile!!
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
  fun WSLDistribution.resolveM2Dir(): File {
    return this.getWindowsFile(File(this.userHome, DOT_M2_DIR))!!
  }

  /**
   * return file in windows-style ("\\wsl$\distrib-name\home\user\.m2\settings.xml")
   */
  @JvmStatic
  fun WSLDistribution.resolveMavenHomeDirectory(overrideMavenHome: String?): File? {
    MavenLog.LOG.debug("resolving maven home on WSL with override = \"${overrideMavenHome}\"")
    if (overrideMavenHome != null) {
      val home = this.getWindowsPath(overrideMavenHome)?.let(::File)
      if (isValidMavenHome(home)) {
        MavenLog.LOG.debug("resolved maven home as ${home}")
        return home
      }
      else {
        MavenLog.LOG.debug("Maven home ${home} on WSL is invalid")
        return null
      }
    }
    val m2home = this.environment[ENV_M2_HOME]
    if (m2home != null && !isEmptyOrSpaces(m2home)) {
      val homeFromEnv = this.getWindowsPath(m2home)?.let(::File)
      if (isValidMavenHome(homeFromEnv)) {
        MavenLog.LOG.debug("resolved maven home using \$M2_HOME as ${homeFromEnv}")
        return homeFromEnv
      }
      else {
        MavenLog.LOG.debug("Maven home using \$M2_HOME is invalid")
        return null
      }
    }
    var home = this.getWindowsPath("/usr/share/maven")?.let(::File)
    if (isValidMavenHome(home)) {
      MavenLog.LOG.debug("Maven home found at /usr/share/maven")
      return home
    }

    home = this.getWindowsPath("/usr/share/maven2")?.let(::File)
    if (isValidMavenHome(home)) {
      MavenLog.LOG.debug("Maven home found at /usr/share/maven2")
      return home
    }
    MavenLog.LOG.debug("Maven home not found on ${this.presentableName}")
    return null
  }

  /**
   * return file in windows style
   */
  @JvmStatic
  fun WSLDistribution.resolveLocalRepository(overriddenLocalRepository: String?,
                                             overriddenMavenHome: String?,
                                             overriddenUserSettingsFile: String?): File {
    if (overriddenLocalRepository != null && !isEmptyOrSpaces(overriddenLocalRepository)) {
      return File(overriddenLocalRepository)
    }
    return doResolveLocalRepository(this.resolveUserSettingsFile(overriddenUserSettingsFile),
                                    this.resolveGlobalSettingsFile(overriddenMavenHome))?.let { this.getWindowsFile(it) }
           ?: File(this.resolveM2Dir(), REPOSITORY_DIR)

  }
  @JvmStatic
  fun WSLDistribution.getDefaultMavenDistribution(overriddenMavenHome: String? = null): WslMavenDistribution? {
    val file = this.resolveMavenHomeDirectory(overriddenMavenHome) ?: return null
    val wslFile = this.getWslPath(file.path) ?: return null
    return WslMavenDistribution(this, wslFile, "default")
  }

  @JvmStatic
  fun getJdkPath(wslDistribution: WSLDistribution): String? {
    return wslDistribution.getEnvironmentVariable("JAVA_HOME");
  }

  @JvmStatic
  fun WSLDistribution.getWindowsFile(wslFile: File): File? {
    return FileUtil.toSystemIndependentName(wslFile.path).let(this::getWindowsPath)?.let(::File)
  }

  @JvmStatic
  fun WSLDistribution.getWslFile(windowsFile: File): File? {
    return windowsFile.path.let(this::getWslPath)?.let(::File)
  }
}