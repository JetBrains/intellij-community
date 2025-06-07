package com.intellij.mcpserver.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "McpServerPluginSettings", storages = [Storage("mcpServer.xml")])
internal class PluginSettings : SimplePersistentStateComponent<MyState>(MyState()){}

internal class MyState : BaseState() {
    var shouldShowNodeNotification: Boolean by property(true)
    var shouldShowClaudeNotification: Boolean by property(true)
    var shouldShowClaudeSettingsNotification: Boolean by property(true)
    var enableBraveMode: Boolean by property(false)
    var enableMcpServer: Boolean by property(false)
}