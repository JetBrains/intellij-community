// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea

import com.intellij.ide.plugins.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinIdePlugin
import org.jetbrains.kotlin.idea.update.verify


@Service
class KotlinPluginUpdater : StandalonePluginUpdateChecker(
    KotlinIdePlugin.id,
    PROPERTY_NAME,
    notificationGroup = null,
    KotlinIcons.SMALL_LOGO
) {
    override val currentVersion: String
        get() = KotlinIdePlugin.version

    override fun skipUpdateCheck() = KotlinIdePlugin.isSnapshot || KotlinIdePlugin.hasPatchedVersion

    override fun verifyUpdate(status: PluginUpdateStatus.Update): PluginUpdateStatus {
        return verify(status)
    }

    companion object {

        private const val PROPERTY_NAME = "kotlin.lastUpdateCheck"

        fun getInstance(): KotlinPluginUpdater = service()
    }
}

