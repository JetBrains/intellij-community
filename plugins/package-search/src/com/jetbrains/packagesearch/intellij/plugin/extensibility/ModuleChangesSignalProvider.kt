package com.jetbrains.packagesearch.intellij.plugin.extensibility

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.createNestedDisposable
import com.intellij.util.messages.MessageBus
import com.intellij.util.messages.MessageBusConnection
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.IVoidSource
import com.jetbrains.rd.util.reactive.Signal
import com.jetbrains.rd.util.reactive.flowInto
import kotlin.streams.asSequence

interface ModuleChangesSignalProvider {

    companion object {

        private val extensionPointName: ExtensionPointName<ModuleChangesSignalProvider> =
            ExtensionPointName.create("com.intellij.packagesearch.moduleChangesSignalProvider")

        fun obtainModuleChangesSignalFor(project: Project, lifetime: Lifetime): IVoidSource =
            extensionPointName.extensions(project)
                .asSequence()
                .map { it.listenToModuleChanges(project, lifetime) }
                .combine(lifetime)

        private fun Sequence<IVoidSource>.combine(lifetime: Lifetime): IVoidSource {
            val combined = Signal.Void()
            forEach { it.flowInto(lifetime, combined) }
            return combined
        }
    }

    fun listenToModuleChanges(project: Project, lifetime: Lifetime): IVoidSource
}

fun MessageBus.connect(lifetime: Lifetime): MessageBusConnection = connect(lifetime.createNestedDisposable())
