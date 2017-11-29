package com.jetbrains.edu.learning.intellij

import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.extensions.PluginId

class EduIntellijApplicationComponent : ApplicationComponent {
    override fun initComponent() {
        val id = "com.jetbrains.edu.intellij"
        val plugin = PluginManager.getPlugin(PluginId.getId(id)) ?: return
        if (plugin.isEnabled) {
            PluginManagerCore.disablePlugin(id)
        }
    }
}
