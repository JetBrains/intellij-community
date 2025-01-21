// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven

import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerCore.isDisabled
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.idea.maven.server.MavenDistribution
import org.jetbrains.idea.maven.server.MavenDistributionsCache

internal object MavenVersionSupportUtil {
  const val MAVEN_2_PLUGIN_ID: String = "org.jetbrains.idea.maven.maven2-support"

  fun getExtensionFor(distribution: MavenDistribution): MavenVersionAwareSupportExtension? {
    return MavenVersionAwareSupportExtension.MAVEN_VERSION_SUPPORT
      .findFirstSafe { e -> e.isSupportedByExtension(distribution.mavenHome.toFile()) }
  }

  @JvmStatic
  val isMaven2PluginInstalled: Boolean
    get() = PluginManager.isPluginInstalled(
      PluginId.getId(MAVEN_2_PLUGIN_ID))

  @JvmStatic
  val isMaven2PluginDisabled: Boolean
    get() = isMaven2PluginInstalled && isDisabled(
      PluginId.getId(MAVEN_2_PLUGIN_ID))

  fun isMaven2Used(project: Project): Boolean {
    val version = MavenDistributionsCache.getInstance(project).getSettingsDistribution().version
    if (version == null) return false
    return StringUtil.compareVersionNumbers(version, "3") < 0
  }
}
