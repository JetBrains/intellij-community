package com.jetbrains.packagesearch.intellij.plugin.util

import com.intellij.ProjectTopics
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.util.Function
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ModuleChangesSignalProvider
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ModuleTransformer
import com.jetbrains.packagesearch.intellij.plugin.lifecycle.ProjectLifecycleHolderService
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageSearchDataService
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageOperationType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.Semaphore
import kotlin.time.milliseconds
import kotlin.time.seconds

internal val Project.packageSearchDataService
    get() = service<PackageSearchDataService>()

@Suppress("BlockingMethodInNonBlockingContext")
internal val Project.nativeModulesChangesFlow
    get() = callbackFlow {
        val buffer = getNativeModules().toMutableList()
        send(buffer)
        val connection = messageBus.simpleConnect()
        connection.subscribe(
            ProjectTopics.MODULES,
            object : ModuleListener {
                override fun moduleAdded(project: Project, module: Module) {
                    synchronized(buffer) { buffer.add(module) }
                    offer(buffer)
                }

                override fun moduleRemoved(project: Project, module: Module) {
                    synchronized(buffer) { buffer.remove(module) }
                    offer(buffer)
                }

                override fun modulesRenamed(project: Project, modules: MutableList<out Module>, oldNameProvider: Function<in Module, String>) {
                    synchronized(buffer) {
                        val oldNames: List<String> = modules.map { oldNameProvider.`fun`(it) }
                        buffer.removeIf { it.name in oldNames }
                        buffer.addAll(modules)
                    }
                    offer(buffer)
                }
            }
        )
        awaitClose { connection.disconnect() }
    }.debounce(200.milliseconds).map { it.toList() }

internal val Project.packageSearchModulesChangesFlow
    get() = nativeModulesChangesFlow.replayOnSignal(moduleChangesSignalFlow)
        .map { modules -> moduleTransformers.flatMapTransform(modules) }

internal fun Project.getNativeModules(): Array<Module> = ModuleManager.getInstance(this).modules

internal val Project.moduleChangesSignalFlow
    get() = ModuleChangesSignalProvider.listenToModuleChanges(this)

internal val Project.moduleTransformers
    get() = ModuleTransformer.getAllModuleTransformersFor(this)

internal fun List<ModuleTransformer>.flatMapTransform(nativeModule: List<Module>) =
    flatMap { it.transformModules(nativeModule) }

internal fun List<ModuleTransformer>.flatMapTransform(nativeModule: Array<Module>) =
    flatMap { it.transformModules(nativeModule) }

internal val Project.lifecycleScope: CoroutineScope
    get() = service<ProjectLifecycleHolderService>()
