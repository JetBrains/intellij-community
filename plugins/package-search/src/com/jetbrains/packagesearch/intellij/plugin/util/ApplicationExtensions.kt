package com.jetbrains.packagesearch.intellij.plugin.util

import com.intellij.ide.PowerSaveMode
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.application.Application
import com.intellij.util.messages.Topic
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

fun <L : Any, K> Application.messageBusFlow(
    topic: Topic<L>,
    initialValue: (suspend () -> K)? = null,
    listener: suspend ProducerScope<K>.() -> L
) = callbackFlow {
    initialValue?.let { send(it()) }
    val connection = messageBus.simpleConnect()
    connection.subscribe(topic, listener())
    awaitClose { connection.disconnect() }
}

enum class KotlinPluginStatus {
    AVAILABLE, UNAVAILABLE
}

private const val KOTLIN_PLUGIN_ID_PREFIX = "org.jetbrains.kotlin"

private fun IdeaPluginDescriptor.isKotlinPlugin(): Boolean = pluginId.idString.startsWith(KOTLIN_PLUGIN_ID_PREFIX)

val Application.kotlinPluginStatusFlow: Flow<KotlinPluginStatus>
    get() = messageBusFlow(
        DynamicPluginListener.TOPIC,
        initialValue = {
            val loaded = PluginManager.getLoadedPlugins().any { it.pluginId.idString.startsWith(KOTLIN_PLUGIN_ID_PREFIX) }
            if (loaded) KotlinPluginStatus.AVAILABLE else KotlinPluginStatus.UNAVAILABLE
        }) {
        object : DynamicPluginListener {
            override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
                if (pluginDescriptor.isKotlinPlugin()) {
                    trySend(KotlinPluginStatus.AVAILABLE)
                }
            }

            override fun pluginUnloaded(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
                if (pluginDescriptor.isKotlinPlugin()) {
                    trySend(KotlinPluginStatus.UNAVAILABLE)
                }
            }
        }
    }

internal enum class PowerSaveModeState {
    ENABLED, DISABLED;

    companion object {
        fun getCurrentState() = if (PowerSaveMode.isEnabled()) ENABLED else DISABLED
    }
}

internal val Application.powerSaveModeFlow
    get() = messageBusFlow(PowerSaveMode.TOPIC, { PowerSaveModeState.getCurrentState() }) {
        PowerSaveMode.Listener { trySend(PowerSaveModeState.getCurrentState()) }
    }

internal val Application.offlineModeFlow
    get() = messageBusFlow(PowerSaveMode.TOPIC, { PowerSaveModeState.getCurrentState() }) {
        PowerSaveMode.Listener { trySend(PowerSaveModeState.getCurrentState()) }
    }
