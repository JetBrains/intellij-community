package com.jetbrains.packagesearch.intellij.plugin

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.PluginId

internal object PluginEnvironment {

    const val PACKAGE_SEARCH_NOTIFICATION_GROUP_ID = "packagesearch.notification"

    const val PLUGIN_ID = "com.jetbrains.packagesearch.intellij-plugin"

    val pluginVersion
        get() = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID))?.version
            ?: PackageSearchBundle.message("packagesearch.version.undefined")

    val ideVersion
        get() = ApplicationInfo.getInstance().strictVersion

    val ideBuildNumber
        get() = ApplicationInfo.getInstance().build

    val isTestEnvironment
        get() = ApplicationManager.getApplication().isUnitTestMode || ApplicationManager.getApplication().isHeadlessEnvironment

    val isNonModalLoadingEnabled
        get() = System.getProperty("idea.pkgs.disableLoading") != "true" && !isTestEnvironment

    val cachesVersion
        get() = 1
}
