package com.jetbrains.packagesearch.intellij.plugin

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.extensions.PluginId

internal const val PACKAGE_SEARCH_NOTIFICATION_GROUP_ID = "packagesearch.notification"

internal class PluginEnvironment {

    val pluginVersion
        get() = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID))?.version
            ?: PackageSearchBundle.message("packagesearch.version.undefined")

    val ideVersion
        get() = ApplicationInfo.getInstance().strictVersion

    val ideBuildNumber
        get() = ApplicationInfo.getInstance().build

    companion object {

        const val PLUGIN_ID = "com.jetbrains.packagesearch.intellij-plugin"
    }
}
