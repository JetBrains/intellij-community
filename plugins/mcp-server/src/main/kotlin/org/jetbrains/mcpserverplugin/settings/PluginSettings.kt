package org.jetbrains.mcpserverplugin.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.State
import com.intellij.openapi.components.service

@State(name = "MyPluginSettings", storages = [Storage("mcpServer.xml")])
class PluginSettings : SimplePersistentStateComponent<MyState>(MyState()){}

class MyState : BaseState() {
    var shouldShowNodeNotification: Boolean by property(true)
    var shouldShowClaudeNotification: Boolean by property(true)
    var shouldShowClaudeSettingsNotification: Boolean by property(true)
}