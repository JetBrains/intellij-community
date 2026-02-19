// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution.run

import com.intellij.openapi.project.Project
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.utils.EelPathUtils.FileTransferAttributesStrategy.Companion.copyWithRequiredPosixPermissions
import com.intellij.platform.eel.provider.utils.EelPathUtils.transferContentsIfNonLocal
import org.jetbrains.idea.maven.execution.MavenRunConfiguration
import org.jetbrains.idea.maven.execution.target.MavenRuntimeTargetConfiguration
import org.jetbrains.idea.maven.project.BundledMaven
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.server.MavenDistributionsCache
import java.nio.file.attribute.PosixFilePermission

internal class MavenRuntimeTargetResolver(private val project: Project, private val eel: EelApi) {

  fun resolve(configuration: MavenRunConfiguration): MavenRuntimeTargetConfiguration {
    val mavenCache = MavenDistributionsCache.getInstance(project)
    val workingDirPath = configuration.runnerParameters.workingDirPath
    val mavenDistribution = mavenCache.getMavenDistribution(workingDirPath)
    val mavenHomePath = mavenDistribution.mavenHome.let {
      if (isBundledMavenRequired()) {
        transferContentsIfNonLocal(eel, it, null, copyWithRequiredPosixPermissions(PosixFilePermission.OWNER_EXECUTE))
      }
      else {
        it
      }
    }
    val effectiveMavenHome = mavenHomePath.asEelPath().toString()
    val mavenVersion = mavenDistribution.version ?: ""
    val mavenConfig = MavenRuntimeTargetConfiguration()
    mavenConfig.homePath = effectiveMavenHome
    mavenConfig.versionString = mavenVersion
    return mavenConfig
  }

  private fun isBundledMavenRequired(): Boolean {
    val settings = MavenProjectsManager.getInstance(project).generalSettings
    val homeType = settings.mavenHomeType
    return homeType is BundledMaven
  }
}
