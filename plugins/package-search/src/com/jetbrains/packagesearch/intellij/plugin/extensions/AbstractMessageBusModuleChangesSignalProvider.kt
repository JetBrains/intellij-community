package com.jetbrains.packagesearch.intellij.plugin.extensions

import com.intellij.openapi.project.Project
import com.intellij.util.messages.SimpleMessageBusConnection
import com.intellij.util.messages.Topic
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ModuleChangesSignalProvider
import com.jetbrains.packagesearch.intellij.plugin.extensibility.Subscription
import java.util.function.Supplier

abstract class AbstractMessageBusModuleChangesSignalProvider : ModuleChangesSignalProvider {

    override fun registerModuleChangesListener(project: Project, listener: Supplier<Unit>): Subscription {
        val simpleConnect: SimpleMessageBusConnection = project.messageBus.simpleConnect()
        registerModuleChangesListener(simpleConnect, listener)
        return Subscription { simpleConnect.disconnect() }
    }

    protected abstract fun registerModuleChangesListener(bus: SimpleMessageBusConnection, listener: Supplier<Unit>)
}
