// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.packagesearch.intellij.plugin.util

import com.intellij.ProjectTopics
import com.intellij.facet.FacetManager
import com.intellij.ide.impl.TrustStateListener
import com.intellij.ide.impl.isTrusted
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.ToolWindow
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
import com.jetbrains.packagesearch.intellij.plugin.ui.PkgsUiCommandsService
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.UiStateModifier
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.UiStateSource
import com.jetbrains.packagesearch.intellij.plugin.data.PackageSearchCachesService
import com.jetbrains.packagesearch.intellij.plugin.data.PackageSearchProjectCachesService
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions.PackageVersionNormalizer
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.merge
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

internal val Project.packageSearchProjectService: PackageSearchProjectService
    get() = service()

internal val packageSearchApplicationCaches: PackageSearchCachesService
    get() = service()

internal val packageVersionNormalizer: PackageVersionNormalizer
    get() = packageSearchApplicationCaches.normalizer

internal val Project.packageSearchProjectCachesService: PackageSearchProjectCachesService
    get() = service()

internal val Project.toolWindowManagerFlow: Flow<ToolWindow>
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
): Flow<K> {
    return callbackFlow {
        initialValue?.let { send(it()) }
        val connection = messageBus.simpleConnect()
        connection.subscribe(topic, listener())
        awaitClose { connection.disconnect() }
    }
}

internal val Project.trustedProjectFlow: Flow<Boolean>
    get() {
        return ApplicationManager.getApplication().messageBusFlow(TrustStateListener.TOPIC, { isTrusted() }) {
            object : TrustStateListener {
                override fun onProjectTrusted(project: Project) {
                    if (project == this@trustedProjectFlow) trySend(isTrusted())
                }
            }
        }.distinctUntilChanged()
    }

internal val Project.nativeModulesFlow: Flow<List<Module>>
    get() = messageBusFlow(ProjectTopics.MODULES, { getNativeModules() }) {
        object : ModuleListener {
            override fun modulesAdded(project: Project, modules: List<Module>) {
                trySend(getNativeModules())
            }

            override fun moduleRemoved(project: Project, module: Module) {
                trySend(getNativeModules())
            }

            override fun modulesRenamed(
                project: Project,
                modules: List<Module>,
                oldNameProvider: Function<in Module, String>
            ) {
                trySend(getNativeModules())
            }
        }
    }

val Project.filesChangedEventFlow: Flow<List<VFileEvent>>
    get() = messageBusFlow(VirtualFileManager.VFS_CHANGES) {
        object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                trySend(events)
            }
        }
    }

internal fun Project.getNativeModules(): List<Module> = ModuleManager.getInstance(this).modules.toList()

internal val Project.moduleChangesSignalFlow: Flow<Unit>
    get() = merge(
        *ModuleChangesSignalProvider.extensions(this),
        *FlowModuleChangesSignalProvider.extensions(this)
    )

internal val Project.lifecycleScope: PackageSearchLifecycleScope
    get() = service()

internal val ProjectModule.lifecycleScope: PackageSearchLifecycleScope
    get() = nativeModule.project.lifecycleScope

internal val Project.pkgsUiStateModifier: UiStateModifier
    get() = service<PkgsUiCommandsService>()

internal val Project.uiStateSource: UiStateSource
    get() = service<PkgsUiCommandsService>()

val Project.dumbService: DumbService
    get() = DumbService.getInstance(this)

suspend fun DumbService.awaitSmart() {
    suspendCoroutine {
        runWhenSmart { it.resume(Unit) }
    }
}

internal val Project.moduleTransformers: List<CoroutineModuleTransformer>
    get() = CoroutineModuleTransformer.extensions(this) + ModuleTransformer.extensions(this)

internal val Project.lookAndFeelFlow: Flow<LafManager>
    get() = messageBusFlow(LafManagerListener.TOPIC, { LafManager.getInstance()!! }) {
        LafManagerListener { trySend(it) }
    }

fun Project.hasKotlinModules(): Boolean = ModuleManager.getInstance(this).modules.any { it.hasKotlinFacet() }

internal fun Module.hasKotlinFacet(): Boolean {
    val facetManager = FacetManager.getInstance(this)
    return facetManager.allFacets.any { it.typeId.toString() == "kotlin-language" }
}
