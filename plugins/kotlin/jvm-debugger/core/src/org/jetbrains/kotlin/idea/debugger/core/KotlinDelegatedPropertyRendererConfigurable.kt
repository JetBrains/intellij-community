// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.core

import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.kotlin.idea.debugger.KotlinDebuggerSettings

internal class KotlinDelegatedPropertyRendererConfigurable(private val settings: KotlinDebuggerSettings) : BoundSearchableConfigurable(
    KotlinDebuggerCoreBundle.message("configurable.name.kotlin"), "reference.idesettings.debugger.kotlin.data.view"
) {

    override fun createPanel(): DialogPanel = panel {
        row {
            checkBox(KotlinDebuggerCoreBundle.message("variables.disable.coroutine.agent.values")).bindSelected(
                { !settings.debugDisableCoroutineAgent },
                { settings.debugDisableCoroutineAgent = !it })
                .applyToComponent { toolTipText = KotlinDebuggerCoreBundle.message("variables.disable.coroutine.agent.tooltip") }
        }
    }
}
