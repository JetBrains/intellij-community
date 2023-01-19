// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.update

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginUpdateStatus
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.kotlin.idea.base.util.KotlinPlatformUtils

// Do an additional verification with PluginUpdateVerifier. Enabled only in AS 3.2+
fun verify(updateStatus: PluginUpdateStatus.Update): PluginUpdateStatus {
    if (!KotlinPlatformUtils.isAndroidStudio) {
        return updateStatus
    }
    @Suppress("UnresolvedPluginConfigReference")
    val pluginVerifierEnabled = Registry.`is`("kotlin.plugin.update.verifier.enabled", true)
    if (!pluginVerifierEnabled) {
        return updateStatus
    }

    val pluginDescriptor: IdeaPluginDescriptor = updateStatus.pluginDescriptor
    val pluginVerifiers = PluginUpdateVerifier.EP_NAME.extensions

    for (pluginVerifier in pluginVerifiers) {
        val verifyResult = pluginVerifier.verify(pluginDescriptor) ?: continue
        if (!verifyResult.verified) {
            return PluginUpdateStatus.Unverified(
                pluginVerifier.verifierName,
                verifyResult.declineMessage,
                updateStatus
            )
        }
    }

    return updateStatus
}