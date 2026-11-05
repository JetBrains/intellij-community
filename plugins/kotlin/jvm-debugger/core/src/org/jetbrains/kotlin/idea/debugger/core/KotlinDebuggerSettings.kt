// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// The package directive doesn't match the file location to prevent API breakage
package org.jetbrains.kotlin.idea.debugger

import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.options.Configurable
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.settings.DebuggerSettingsCategory
import com.intellij.xdebugger.settings.XDebuggerSettings
import org.jetbrains.kotlin.idea.debugger.core.KotlinDelegatedPropertyRendererConfigurable
import org.jetbrains.kotlin.idea.debugger.core.stepping.KotlinSteppingConfigurable

@State(name = "KotlinDebuggerSettings", storages = [Storage("kotlin_debug.xml")])
class KotlinDebuggerSettings : XDebuggerSettings<KotlinDebuggerSettings>("kotlin_debugger") {

    var renderDelegatedProperties: Boolean = false
    var disableKotlinInternalClasses: Boolean = true
    var debugDisableCoroutineAgent: Boolean = false
    var alwaysDoSmartStepInto: Boolean = true

    companion object {
        fun getInstance(): KotlinDebuggerSettings {
            return XDebuggerUtil.getInstance()?.getDebuggerSettings(KotlinDebuggerSettings::class.java)!!
        }
    }

    override fun createConfigurables(category: DebuggerSettingsCategory): Collection<Configurable?> = when (category) {
        DebuggerSettingsCategory.STEPPING -> listOf(KotlinSteppingConfigurable(this))
        DebuggerSettingsCategory.GENERAL -> listOf(KotlinDelegatedPropertyRendererConfigurable(this))
        else -> listOf()
    }

    override fun getState(): KotlinDebuggerSettings = this

    override fun loadState(state: KotlinDebuggerSettings) {
        XmlSerializerUtil.copyBean<KotlinDebuggerSettings>(state, this)
    }
}
