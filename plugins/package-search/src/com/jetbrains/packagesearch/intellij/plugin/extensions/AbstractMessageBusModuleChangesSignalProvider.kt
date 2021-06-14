package com.jetbrains.packagesearch.intellij.plugin.extensions

import com.intellij.openapi.project.Project
import com.intellij.util.messages.SimpleMessageBusConnection
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ModuleChangesSignalProvider
import com.jetbrains.packagesearch.intellij.plugin.extensibility.Subscription
import com.jetbrains.packagesearch.intellij.plugin.extensibility.invoke
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier

abstract class AbstractMessageBusModuleChangesSignalProvider : ModuleChangesSignalProvider {

    override fun registerModuleChangesListener(project: Project, listener: Supplier<Unit>): Subscription {
        val isSubscribed = AtomicBoolean(true)
        val simpleConnect: SimpleMessageBusConnection = project.messageBus.simpleConnect()
        registerModuleChangesListener(project, simpleConnect) { if (isSubscribed.get()) listener() }
        return Subscription {
            isSubscribed.set(false)
            simpleConnect.disconnect()
        }
    }

    protected abstract fun registerModuleChangesListener(project: Project, bus: SimpleMessageBusConnection, listener: Supplier<Unit>)
}
