/*******************************************************************************
 * Copyright 2000-2022 JetBrains s.r.o. and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

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

    private val isTestEnvironment
        get() = ApplicationManager.getApplication().isUnitTestMode || ApplicationManager.getApplication().isHeadlessEnvironment

    val isNonModalLoadingEnabled
        get() = System.getProperty("idea.pkgs.disableLoading") != "true" && !isTestEnvironment

    object Caches {

        val version
            get() = 3

        val maxAttempts
            get() = System.getProperty("idea.pkgs.caches.attempts")?.toInt() ?: 30
    }
}
