// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent
import org.jetbrains.idea.maven.utils.MavenUtil
import java.io.File

interface MavenDistribution {
  val name: String
  val mavenHome: File
  val version: String?
  fun isValid(): Boolean
  fun compatibleWith(mavenDistribution: MavenDistribution): Boolean

  companion object {
    @JvmStatic
    fun fromSettings(project: Project?): MavenDistribution? {
      val mavenHome = MavenWorkspaceSettingsComponent.getInstance(project).settings.generalSettings.mavenHome
      return MavenDistributionConverter().fromString(mavenHome)
    }
  }

}

class LocalMavenDistribution(override val mavenHome: File, override val name: String) : MavenDistribution {
  override val version: String? by lazy {
    MavenUtil.getMavenVersion(mavenHome)
  }

  override fun compatibleWith(mavenDistribution: MavenDistribution): Boolean {
    return mavenDistribution == this || FileUtil.filesEqual(mavenDistribution.mavenHome, mavenHome)
  }

  override fun isValid() = version != null
  override fun toString(): String {
    return name + "(" + mavenHome + ") v " + version
  }
}

class WslMavenDistribution(private val wslDistribution: WSLDistribution,
                           val pathToMaven: String,
                           override val name: String) : MavenDistribution {
  override val version: String? by lazy {
    MavenUtil.getMavenVersion(wslDistribution.getWindowsPath(pathToMaven))
  }

  override val mavenHome = File(wslDistribution.getWindowsPath(pathToMaven)!!)

  override fun compatibleWith(mavenDistribution: MavenDistribution): Boolean {
    if (mavenDistribution == this) return true
    val another = mavenDistribution as? WslMavenDistribution ?: return false;
    return another.wslDistribution == wslDistribution && another.pathToMaven == pathToMaven
  }

  override fun isValid() = version != null
  override fun toString(): String {
    return name + "(" + mavenHome + ") v " + version
  }
}