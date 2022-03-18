package com.jetbrains.packagesearch.intellij.plugin.util

import com.intellij.ProjectTopics
import com.intellij.ide.impl.TrustStateListener
import com.intellij.ide.impl.isTrusted
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.Function
import com.intellij.util.messages.Topic
import com.jetbrains.packagesearch.intellij.plugin.data.PackageSearchProjectService
import com.jetbrains.packagesearch.intellij.plugin.extensibility.CoroutineModuleTransformer
import com.jetbrains.packagesearch.intellij.plugin.extensibility.FlowModuleChangesSignalProvider
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ModuleChangesSignalProvider
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ModuleTransformer
import com.jetbrains.packagesearch.intellij.plugin.lifecycle.ProjectLifecycleHolderService
import com.jetbrains.packagesearch.intellij.plugin.ui.UiCommandsService
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.UiStateModifier
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.UiStateSource
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions.PackageSearchCachesService
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions.PackageSearchProjectCachesService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.merge
import kotlin.experimental.ExperimentalTypeInference
import kotlin.streams.toList

internal val Project.packageSearchProjectService
    get() = service<PackageSearchProjectService>()

internal val packageSearchApplicationCaches
    get() = service<PackageSearchCachesService>()

internal val packageVersionNormalizer
    get() = packageSearchApplicationCaches.normalizer

internal val Project.packageSearchProjectCachesService
    get() = service<PackageSearchProjectCachesService>()

@OptIn(ExperimentalTypeInference::class)
internal fun <L : Any, K> Project.messageBusFlow(
    topic: Topic<L>,
    initialValue: (suspend () -> K)? = null,
    @BuilderInference listener: ProducerScope<K>.() -> L
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

internal val Project.nativeModulesChangesFlow
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

internal val Project.filesChangedEventFlow
    get() = messageBusFlow(VirtualFileManager.VFS_CHANGES) {
        object : BulkFileListener {
            override fun after(events: MutableList<out VFileEvent>) {
                trySend(events)
            }
        }
    }

internal fun Project.getNativeModules(): List<Module> = ModuleManager.getInstance(this).modules.toList()

internal val Project.moduleChangesSignalFlow
    get() = merge(ModuleChangesSignalProvider.listenToModuleChanges(this), FlowModuleChangesSignalProvider.listenToModuleChanges(this))

internal val Project.lifecycleScope: CoroutineScope
    get() = service<ProjectLifecycleHolderService>()

internal val Project.uiStateModifier: UiStateModifier
    get() = service<UiCommandsService>()

internal val Project.uiStateSource: UiStateSource
    get() = service<UiCommandsService>()

internal val Project.dumbService: DumbService
    get() = DumbService.getInstance(this)

internal val Project.moduleTransformers: List<ModuleTransformer>
    get() = ModuleTransformer.extensionPointName.extensions(this).toList()

internal val Project.coroutineModuleTransformers: List<CoroutineModuleTransformer>
    get() = CoroutineModuleTransformer.extensionPointName.extensions(this).toList()

internal val Project.lookAndFeelFlow
    get() = messageBusFlow(LafManagerListener.TOPIC, { LafManager.getInstance()!! }) {
        LafManagerListener { trySend(it) }
    }

internal val Project.toolWindowManager
    get() = service<ToolWindowManager>()
