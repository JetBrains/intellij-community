// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server

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
  fun compatibleWith(distribution: MavenDistribution): Boolean

  companion object {
    @JvmStatic
    fun fromSettings(project: Project?): MavenDistribution? {
      val mavenHome = MavenWorkspaceSettingsComponent.getInstance(project).settings.generalSettings.mavenHome
      return MavenDistributionConverter().fromString(mavenHome)
    }
  }


}

class LocalMavenDistribution(override val mavenHome: File, override val name: String) : MavenDistribution {
  override val version = MavenUtil.getMavenVersion(mavenHome)
  override fun compatibleWith(distribution: MavenDistribution): Boolean {
    return distribution == this || FileUtil.filesEqual(distribution.mavenHome, mavenHome)
  }
  override fun isValid() = version != null
  override fun toString(): String {
    return name + "(" + mavenHome + ") v " + version
  }
}