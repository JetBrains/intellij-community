package com.jetbrains.packagesearch.intellij.plugin.extensibility

import com.intellij.openapi.project.Project
import com.intellij.util.messages.SimpleMessageBusConnection
import com.jetbrains.packagesearch.intellij.plugin.util.dumbService
import java.util.concurrent.atomic.AtomicBoolean

abstract class AbstractMessageBusModuleChangesSignalProvider : ModuleChangesSignalProvider {

    override fun registerModuleChangesListener(project: Project, listener: Runnable): Subscription {
        val isSubscribed = AtomicBoolean(true)
        val simpleConnect: SimpleMessageBusConnection = project.messageBus.simpleConnect()
        registerModuleChangesListener(project, simpleConnect) { if (isSubscribed.get()) listener() }
        return Subscription {
            isSubscribed.set(false)
            simpleConnect.disconnect()
        }
    }

    protected abstract fun registerModuleChangesListener(project: Project, bus: SimpleMessageBusConnection, listener: Runnable)
}

abstract class DumbAwareMessageBusModuleChangesSignalProvider : AbstractMessageBusModuleChangesSignalProvider() {

    override fun registerModuleChangesListener(project: Project, bus: SimpleMessageBusConnection, listener: Runnable) =
        registerDumbAwareModuleChangesListener(project, bus) { project.dumbService.runWhenSmart(listener) }

    abstract fun registerDumbAwareModuleChangesListener(project: Project, bus: SimpleMessageBusConnection, listener: Runnable)
}
