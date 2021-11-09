package com.jetbrains.packagesearch.intellij.plugin.util

import com.intellij.ProjectTopics
import com.intellij.ide.impl.TrustChangeListener
import com.intellij.ide.impl.getTrustedState
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.Function
import com.intellij.util.ThreeState
import com.jetbrains.packagesearch.intellij.plugin.data.PackageSearchProjectService
import com.jetbrains.packagesearch.intellij.plugin.extensibility.CoroutineModuleTransformer
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ModuleChangesSignalProvider
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ModuleTransformer
import com.jetbrains.packagesearch.intellij.plugin.lifecycle.ProjectLifecycleHolderService
import com.jetbrains.packagesearch.intellij.plugin.ui.UiCommandsService
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.UiStateModifier
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.UiStateSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlin.streams.toList

internal val Project.packageSearchProjectService
    get() = service<PackageSearchProjectService>()

internal val Project.trustedProjectFlow: Flow<ThreeState>
    get() = callbackFlow {
        send(getTrustedState())
        val connection = messageBus.simpleConnect()
        connection.subscribe(
            TrustChangeListener.TOPIC,
            TrustChangeListener {
                if (it == this@trustedProjectFlow) trySend(getTrustedState())
            }
        )
        awaitClose { connection.disconnect() }
    }.distinctUntilChanged()

@Suppress("BlockingMethodInNonBlockingContext")
internal val Project.nativeModulesChangesFlow
    get() = callbackFlow {
        send(getNativeModules())

        val connection = messageBus.simpleConnect()
        connection.subscribe(
            ProjectTopics.MODULES,
            object : ModuleListener {
                override fun moduleAdded(project: Project, module: Module) {
                    trySend(getNativeModules())
                }

                override fun moduleRemoved(project: Project, module: Module) {
                    trySend(getNativeModules())
                }

                override fun modulesRenamed(project: Project, modules: MutableList<out Module>, oldNameProvider: Function<in Module, String>) {
                    trySend(getNativeModules())
                }
            }
        )
        awaitClose { connection.disconnect() }
    }.mapLatest { it.toList() }

internal fun Project.getNativeModules(): Array<Module> = ModuleManager.getInstance(this).modules

internal val Project.moduleChangesSignalFlow
    get() = ModuleChangesSignalProvider.listenToModuleChanges(this)

internal fun List<ModuleTransformer>.flatMapTransform(project: Project, nativeModule: List<Module>) =
    flatMap { it.transformModules(project, nativeModule) }

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

internal val Project.coroutineModuleTransformer: List<CoroutineModuleTransformer>
    get() = CoroutineModuleTransformer.extensionPointName.extensions(this).toList()

internal val Project.lookAndFeelFlow
    get() = callbackFlow {
        val connection = messageBus.simpleConnect()
        send(LafManager.getInstance()!!)
        connection.subscribe(
            LafManagerListener.TOPIC,
            LafManagerListener { trySend(it) }
        )
        awaitClose { connection.disconnect() }
    }

internal val Project.toolWindowManager
    get() = service<ToolWindowManager>()
