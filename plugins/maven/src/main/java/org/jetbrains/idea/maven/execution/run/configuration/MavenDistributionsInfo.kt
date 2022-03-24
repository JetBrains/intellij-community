// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution.run.configuration

import com.intellij.ide.util.BrowseFilesListener
import com.intellij.openapi.externalSystem.service.ui.util.DistributionsInfo
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.roots.ui.distribution.AbstractDistributionInfo
import com.intellij.openapi.roots.ui.distribution.DistributionInfo
import com.intellij.openapi.roots.ui.distribution.LocalDistributionInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.idea.maven.project.MavenConfigurableBundle
import org.jetbrains.idea.maven.project.MavenProjectBundle
import org.jetbrains.idea.maven.server.MavenServerManager
import org.jetbrains.idea.maven.utils.MavenUtil

class MavenDistributionsInfo : DistributionsInfo {
  override val editorLabel: String = MavenConfigurableBundle.message("maven.run.configuration.distribution.label")

  override val settingsName: String = MavenConfigurableBundle.message("maven.run.configuration.distribution.name")
  override val settingsHint: String? = null

  override val comboBoxActionName: String = MavenConfigurableBundle.message("maven.run.configuration.specify.distribution.action.name")

  override val fileChooserTitle: String = MavenProjectBundle.message("maven.select.maven.home.directory")
  override val fileChooserDescription: String? = null
  override val fileChooserDescriptor: FileChooserDescriptor = BrowseFilesListener.SINGLE_DIRECTORY_DESCRIPTOR

  override val distributions: List<DistributionInfo> by lazy {
    ArrayList<DistributionInfo>().apply {
      addIfNotNull(asDistributionInfo(MavenServerManager.BUNDLED_MAVEN_3))
      addIfNotNull(asDistributionInfo(MavenServerManager.WRAPPED_MAVEN))
      val mavenHomeDirectory = MavenUtil.resolveMavenHomeDirectory(null)
      val bundledMavenHomeDirectory = MavenUtil.resolveMavenHomeDirectory(MavenServerManager.BUNDLED_MAVEN_3)
      if (mavenHomeDirectory != null && !FileUtil.filesEqual(mavenHomeDirectory, bundledMavenHomeDirectory)) {
        addIfNotNull(asDistributionInfo(mavenHomeDirectory.path))
      }
    }
  }

  open class BundledDistributionInfo(version: String) : AbstractDistributionInfo() {
    override val name: String = MavenConfigurableBundle.message("maven.run.configuration.bundled.distribution.name", version)
    override val description: String = MavenConfigurableBundle.message("maven.run.configuration.bundled.distribution.description")
  }

  class Bundled2DistributionInfo(version: String?) : BundledDistributionInfo(version ?: "2")
  class Bundled3DistributionInfo(version: String?) : BundledDistributionInfo(version ?: "3")

  class WrappedDistributionInfo : AbstractDistributionInfo() {
    override val name: String = MavenProjectBundle.message("maven.wrapper.version.title")
    override val description: String? = null
  }

  companion object {
    fun asDistributionInfo(mavenHome: String): DistributionInfo {
      val version = MavenServerManager.getMavenVersion(mavenHome)
      return when (mavenHome) {
        MavenServerManager.BUNDLED_MAVEN_2 -> Bundled2DistributionInfo(version)
        MavenServerManager.BUNDLED_MAVEN_3 -> Bundled3DistributionInfo(version)
        MavenServerManager.WRAPPED_MAVEN -> WrappedDistributionInfo()
        else -> LocalDistributionInfo(mavenHome)
      }
    }

    fun asMavenHome(distribution: DistributionInfo): String {
      return when (distribution) {
        is Bundled2DistributionInfo -> MavenServerManager.BUNDLED_MAVEN_2
        is Bundled3DistributionInfo -> MavenServerManager.BUNDLED_MAVEN_3
        is WrappedDistributionInfo -> MavenServerManager.WRAPPED_MAVEN
        is LocalDistributionInfo -> distribution.path
        else -> throw NoWhenBranchMatchedException(distribution.javaClass.toString())
      }
    }
  }
}