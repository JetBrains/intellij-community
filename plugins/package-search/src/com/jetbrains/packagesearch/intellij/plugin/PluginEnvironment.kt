package com.jetbrains.packagesearch.intellij.plugin

import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.extensions.PluginId

const val PACKAGE_SEARCH_NOTIFICATION_GROUP_ID = "PACKAGESEARCH.NOTIFICATION"

class PluginEnvironment {

    val pluginVersion
        get() = PluginManager.getPlugin(PluginId.getId("com.jetbrains.packagesearch.intellij-plugin"))?.version
            ?: PackageSearchBundle.message("packagesearch.version.undefined")

    val ideVersion
        get() = ApplicationInfo.getInstance().strictVersion!!
}
