package com.jetbrains.packagesearch.intellij.plugin

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.NlsSafe

const val PACKAGE_SEARCH_NOTIFICATION_GROUP_ID = "PACKAGESEARCH.NOTIFICATION"

class PluginEnvironment {
  val pluginVersion: @NlsSafe String
    get() {
      return PluginManagerCore.getPlugin(PluginId.getId("com.jetbrains.packagesearch.intellij-plugin"))?.version
             ?: PackageSearchBundle.message("packagesearch.version.undefined")
    }

  val ideVersion
    get() = ApplicationInfo.getInstance().strictVersion
}
