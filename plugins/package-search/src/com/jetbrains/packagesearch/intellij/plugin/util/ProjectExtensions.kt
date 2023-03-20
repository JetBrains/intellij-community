// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.packagesearch.intellij.plugin.util

import com.intellij.ProjectTopics
import com.intellij.externalSystem.DependencyModifierService
import com.intellij.facet.FacetManager
import com.intellij.ide.impl.isTrusted
import com.intellij.ide.trustedProjects.TrustedProjectsListener
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.Function
import com.intellij.util.messages.Topic
import com.jetbrains.packagesearch.intellij.plugin.data.LoadingContainer
import com.jetbrains.packagesearch.intellij.plugin.data.PackageSearchCachesService
import com.jetbrains.packagesearch.intellij.plugin.data.PackageSearchProjectCachesService
import com.jetbrains.packagesearch.intellij.plugin.data.PackageSearchProjectService
import com.jetbrains.packagesearch.intellij.plugin.extensibility.AsyncModuleTransformer
import com.jetbrains.packagesearch.intellij.plugin.extensibility.FlowModuleChangesSignalProvider
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ModuleChangesSignalProvider
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ModuleTransformer
import com.jetbrains.packagesearch.intellij.plugin.extensibility.PackageSearchModule
import com.jetbrains.packagesearch.intellij.plugin.lifecycle.PackageSearchLifecycleScope
import com.jetbrains.packagesearch.intellij.plugin.ui.PkgsUiCommandsService
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.UiStateModifier
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions.PackageVersionNormalizer
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.PackageManagementOperationExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
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
        return ApplicationManager.getApplication().messageBusFlow(TrustedProjectsListener.TOPIC, { isTrusted() }) {
            object : TrustedProjectsListener {
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
                modules: MutableList<out Module>,
                oldNameProvider: Function<in Module, String>
            ) {
                trySend(getNativeModules())
            }
        }
    }

val Project.filesChangedEventFlow: Flow<MutableList<out VFileEvent>>
    get() = messageBusFlow(VirtualFileManager.VFS_CHANGES) {
        object : BulkFileListener {
            override fun after(events: MutableList<out VFileEvent>) {
                val result = trySend(events)
                if (result.isFailure) {
                    logDebug("filesChangedEventFlow", throwable = result.exceptionOrNull()) { "Failed to send file change" }
                }
            }
        }
    }

internal fun Project.getNativeModules(): List<Module> = ModuleManager.getInstance(this).modules.toList()

internal val Project.moduleChangesSignalFlow: Flow<Unit>
    get() = merge(
        *ModuleChangesSignalProvider.extensions(this),
        *FlowModuleChangesSignalProvider.extensions(this)
    )

internal val Project.lifecycleScope: CoroutineScope
    get() = service<PackageSearchLifecycleScope>().cs

internal val PackageSearchModule.lifecycleScope: CoroutineScope
    get() = nativeModule.project.lifecycleScope

internal val Project.pkgsUiStateModifier: UiStateModifier
    get() = service<PkgsUiCommandsService>()

val Project.dumbService: DumbService
    get() = DumbService.getInstance(this)

suspend fun DumbService.awaitSmart() {
    suspendCoroutine {
        runWhenSmart { it.resume(Unit) }
    }
}

internal val Project.moduleTransformers: List<ModuleTransformer>
    get() = ModuleTransformer.extensions(this) + AsyncModuleTransformer.extensions(this)

internal val Project.lookAndFeelFlow: Flow<LafManager>
    get() = messageBusFlow(LafManagerListener.TOPIC, { LafManager.getInstance()!! }) {
        LafManagerListener { trySend(it) }
    }

fun Project.hasKotlinModules(): Boolean = ModuleManager.getInstance(this).modules.any { it.hasKotlinFacet() }

internal fun Module.hasKotlinFacet(): Boolean {
    val facetManager = FacetManager.getInstance(this)
    return facetManager.allFacets.any { it.typeId.toString() == "kotlin-language" }
}

internal fun Project.modifyPackages(builder: PackageManagementOperationExecutor.() -> Unit) =
    lifecycleScope.launch {
        PackageManagementOperationExecutor(this@modifyPackages)
            .apply(builder)
            .execute()
    }

val Project.dependencyModifierService
    get() = DependencyModifierService.getInstance(this)

internal val Project.loadingContainer
    get() = service<LoadingContainer>()

internal sealed interface FileEditorEvent {

    val file: VirtualFile

    @JvmInline
    value class FileOpened(override val file: VirtualFile) : FileEditorEvent

    @JvmInline
    value class FileClosed(override val file: VirtualFile) : FileEditorEvent
}

private val Project.project
    get() = this

internal val Project.fileOpenedFlow
    get() = flow {
        val buffer: MutableList<VirtualFile> = FileEditorManager.getInstance(project).openFiles
            .toMutableList()
        emit(buffer.toList())
        messageBusFlow(FileEditorManagerListener.FILE_EDITOR_MANAGER) {
            object : FileEditorManagerListener {
                override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                    trySend(FileEditorEvent.FileOpened(file))
                }

                override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
                    trySend(FileEditorEvent.FileClosed(file))
                }
            }
        }.collect {
            when (it) {
                is FileEditorEvent.FileClosed -> buffer.remove(it.file)
                is FileEditorEvent.FileOpened -> buffer.add(it.file)
            }
            emit(buffer.toList())
        }
    }