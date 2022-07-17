/*******************************************************************************
 * Copyright 2000-2022 JetBrains s.r.o. and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.jetbrains.packagesearch.intellij.plugin.util

import com.intellij.ProjectTopics
import com.intellij.ide.impl.TrustStateListener
import com.intellij.ide.impl.isTrusted
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.Application
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.util.Function
import com.intellij.util.messages.Topic
import com.jetbrains.packagesearch.intellij.plugin.data.PackageSearchProjectService
import com.jetbrains.packagesearch.intellij.plugin.extensibility.CoroutineModuleTransformer
import com.jetbrains.packagesearch.intellij.plugin.extensibility.FlowModuleChangesSignalProvider
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ModuleChangesSignalProvider
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ModuleTransformer
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.lifecycle.PackageSearchLifecycleScope
import com.jetbrains.packagesearch.intellij.plugin.ui.UiCommandsService
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.UiStateModifier
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.UiStateSource
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions.PackageSearchCachesService
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions.PackageSearchProjectCachesService
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.merge
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

internal val Project.packageSearchProjectService
    get() = service<PackageSearchProjectService>()

internal val packageSearchApplicationCaches
    get() = service<PackageSearchCachesService>()

internal val packageVersionNormalizer
    get() = packageSearchApplicationCaches.normalizer

internal val Project.packageSearchProjectCachesService
    get() = service<PackageSearchProjectCachesService>()

internal val Project.toolWindowManagerFlow
    get() = messageBusFlow(ToolWindowManagerListener.TOPIC) {
        object : ToolWindowManagerListener {
            override fun toolWindowShown(toolWindow: ToolWindow) {
                trySend(toolWindow)
            }
        }
    }

fun <L : Any, K> Project.messageBusFlow(
    topic: Topic<L>,
    initialValue: (suspend () -> K)? = null,
    listener: suspend ProducerScope<K>.() -> L
) = callbackFlow {
    initialValue?.let { send(it()) }
    val connection = messageBus.simpleConnect()
    connection.subscribe(topic, listener())
    awaitClose { connection.disconnect() }
}

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

internal val Project.trustedProjectFlow: Flow<Boolean>
    get() = messageBusFlow(TrustStateListener.TOPIC, { isTrusted() }) {
        object : TrustStateListener {
            override fun onProjectTrusted(project: Project) {
                if (project == this@trustedProjectFlow) trySend(isTrusted())
            }
        }
    }.distinctUntilChanged()

internal val Project.nativeModulesFlow
    get() = messageBusFlow(ProjectTopics.MODULES, { getNativeModules() }) {
        object : ModuleListener {
            override fun moduleAdded(project: Project, module: Module) {
                trySend(getNativeModules())
            }

            override fun moduleRemoved(project: Project, module: Module) {
                trySend(getNativeModules())
            }

            override fun modulesRenamed(
                project: Project,
                modules: MutableList<out Module>,
                oldNameProvider: Function<in Module, String>
            ) {
                trySend(getNativeModules())
            }
        }
    }

val Project.filesChangedEventFlow
    get() = messageBusFlow(VirtualFileManager.VFS_CHANGES) {
        object : BulkFileListener {
            override fun after(events: MutableList<out VFileEvent>) {
                trySend(events)
            }
        }
    }

internal fun Project.getNativeModules(): List<Module> = ModuleManager.getInstance(this).modules.toList()

internal val Project.moduleChangesSignalFlow
    get() = merge(
        *ModuleChangesSignalProvider.extensions(this),
        *FlowModuleChangesSignalProvider.extensions(this)
    )

internal val Project.lifecycleScope: PackageSearchLifecycleScope
    get() = service()

internal val ProjectModule.lifecycleScope: PackageSearchLifecycleScope
    get() = nativeModule.project.lifecycleScope

internal val Project.uiStateModifier: UiStateModifier
    get() = service<UiCommandsService>()

internal val Project.uiStateSource: UiStateSource
    get() = service<UiCommandsService>()

val Project.dumbService: DumbService
    get() = DumbService.getInstance(this)

suspend fun DumbService.awaitSmart(): Unit = suspendCoroutine {
    runWhenSmart { it.resume(Unit) }
}

internal val Project.moduleTransformers: List<CoroutineModuleTransformer>
    get() = CoroutineModuleTransformer.extensions(this) + ModuleTransformer.extensions(this)

internal val Project.lookAndFeelFlow
    get() = messageBusFlow(LafManagerListener.TOPIC, { LafManager.getInstance()!! }) {
        LafManagerListener { trySend(it) }
    }

internal val Project.toolWindowManager
    get() = service<ToolWindowManager>()

val <T : Any> ExtensionPointName<T>.extensionsFlow: Flow<List<T>>
    get() = callbackFlow {
        val listener = object : ExtensionPointListener<T> {
            override fun extensionAdded(extension: T, pluginDescriptor: PluginDescriptor) {
                trySendBlocking(extensions.toList())
            }

            override fun extensionRemoved(extension: T, pluginDescriptor: PluginDescriptor) {
                trySendBlocking(extensions.toList())
            }
        }
        send(extensions.toList())
        addExtensionPointListener(listener)
        awaitClose { removeExtensionPointListener(listener) }
    }
