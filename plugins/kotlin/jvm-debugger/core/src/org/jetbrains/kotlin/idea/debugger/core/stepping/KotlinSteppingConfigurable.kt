// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.core.stepping

import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.kotlin.idea.debugger.KotlinDebuggerSettings
import org.jetbrains.kotlin.idea.debugger.core.KotlinDebuggerCoreBundle

internal class KotlinSteppingConfigurable(private val settings: KotlinDebuggerSettings) : BoundSearchableConfigurable(
    KotlinDebuggerCoreBundle.message("configurable.name.kotlin"), "reference.idesettings.debugger.kotlin.stepping"
) {

    override fun createPanel(): DialogPanel = panel {
        row {
            checkBox(KotlinDebuggerCoreBundle.message("filter.ignore.internal.classes")).bindSelected(settings::disableKotlinInternalClasses)
        }
        row {
            checkBox(KotlinDebuggerCoreBundle.message("always.do.smart.step.into")).bindSelected(settings::alwaysDoSmartStepInto)
        }
    }
}
