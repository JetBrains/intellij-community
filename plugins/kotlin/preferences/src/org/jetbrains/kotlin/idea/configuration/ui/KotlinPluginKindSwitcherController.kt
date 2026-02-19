// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.configuration.ui

import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.ui.Messages
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.idea.base.plugin.USE_K1_PLUGIN_PROPERTY_NAME
import org.jetbrains.kotlin.idea.base.plugin.USE_K2_PLUGIN_PROPERTY_NAME

class KotlinPluginKindSwitcherController {

    companion object {
        fun suggestRestart(productName: String) {
            val application = ApplicationManager.getApplication() as ApplicationEx

            val result = Messages.showOkCancelDialog(
                IdeBundle.message("dialog.message.must.be.restarted.for.changes.to.take.effect", productName),
                IdeBundle.message("dialog.title.restart.required"),
                IdeBundle.message("button.now", if (application.isRestartCapable()) 0 else 1),
                IdeBundle.message("button.later", if (application.isRestartCapable()) 0 else 1),
                Messages.getQuestionIcon()
            )

            if (result == Messages.OK) {
                application.restart(/* exitConfirmed = */ true)
            }
        }
    }
}

const val USE_K1_PLUGIN_VM_OPTION_PREFIX: @NonNls String = "-D$USE_K1_PLUGIN_PROPERTY_NAME="

@Deprecated("Use USE_K1_PLUGIN_VM_OPTION_PREFIX instead")
const val USE_K2_PLUGIN_VM_OPTION_PREFIX: @NonNls String = "-D$USE_K2_PLUGIN_PROPERTY_NAME="
