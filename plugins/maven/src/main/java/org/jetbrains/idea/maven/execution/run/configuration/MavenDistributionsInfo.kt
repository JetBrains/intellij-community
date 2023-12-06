// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution.run.configuration

import com.intellij.ide.util.BrowseFilesListener
import com.intellij.openapi.externalSystem.service.ui.util.DistributionsInfo
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.roots.ui.distribution.AbstractDistributionInfo
import com.intellij.openapi.roots.ui.distribution.DistributionInfo
import com.intellij.openapi.roots.ui.distribution.LocalDistributionInfo
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.idea.maven.MavenVersionAwareSupportExtension
import org.jetbrains.idea.maven.maven3.Bundled3DistributionInfo
import org.jetbrains.idea.maven.maven4.Bundled4DistributionInfo
import org.jetbrains.idea.maven.project.*
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
      addIfNotNull(asDistributionInfo(MavenWrapper))
      addAll(MavenUtil.getSystemMavenHomeVariants().map(::asDistributionInfo))
    }
  }

  class WrappedDistributionInfo : AbstractDistributionInfo() {
    override val name: String = MavenProjectBundle.message("maven.wrapper.version.title")
    override val description: String? = null
  }

  companion object {
    fun asDistributionInfo(mavenHomeType: MavenHomeType): DistributionInfo {
      val version = (mavenHomeType as? StaticResolvedMavenHomeType)
        ?.let { MavenUtil.getMavenVersion(MavenUtil.getMavenHomeFile(it)) }

      return when (mavenHomeType) {
        is BundledMaven3 -> Bundled3DistributionInfo(version)
        is BundledMaven4 -> Bundled4DistributionInfo(version)
        is MavenWrapper -> WrappedDistributionInfo()
        is MavenInSpecificPath -> LocalDistributionInfo(mavenHomeType.mavenHome)
        else -> throw NoWhenBranchMatchedException(mavenHomeType.javaClass.toString())
      }
    }

    fun asMavenHome(distribution: DistributionInfo): MavenHomeType {
      return when (distribution) {
        is Bundled3DistributionInfo -> BundledMaven3
        is Bundled4DistributionInfo -> BundledMaven4
        is WrappedDistributionInfo -> MavenWrapper
        is LocalDistributionInfo -> MavenInSpecificPath(distribution.path)
        else -> throw NoWhenBranchMatchedException(distribution.javaClass.toString())
      }
    }
  }
}