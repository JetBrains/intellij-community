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

import com.intellij.buildsystem.model.unified.UnifiedDependencyRepository
import com.intellij.openapi.application.EDT
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.impl.ProgressManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolder
import com.intellij.psi.PsiFile
import com.intellij.util.flow.throttle
import com.jetbrains.packagesearch.intellij.plugin.extensibility.AsyncModuleTransformer
import com.jetbrains.packagesearch.intellij.plugin.extensibility.AsyncProjectModuleOperationProvider
import com.jetbrains.packagesearch.intellij.plugin.extensibility.CoroutineModuleTransformer
import com.jetbrains.packagesearch.intellij.plugin.extensibility.CoroutineProjectModuleOperationProvider
import com.jetbrains.packagesearch.intellij.plugin.extensibility.DependencyOperationMetadata
import com.jetbrains.packagesearch.intellij.plugin.extensibility.FlowModuleChangesSignalProvider
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ModuleChangesSignalProvider
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ModuleTransformer
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleOperationProvider
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.future.await
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration
import kotlin.time.TimedValue
import kotlin.time.measureTimedValue

internal fun <T> Flow<T>.onEach(context: CoroutineContext, action: suspend (T) -> Unit) =
    onEach { withContext(context) { action(it) } }

internal fun <T, R> Flow<T>.map(context: CoroutineContext, action: suspend (T) -> R) =
    map { withContext(context) { action(it) } }

internal fun <T> Flow<T>.replayOnSignals(vararg signals: Flow<Any>) = channelFlow {
    var lastValue: T? = null
    onEach { send(it) }
        .onEach { lastValue = it }
        .launchIn(this)

    merge(*signals)
        .onEach { lastValue?.let { send(it) } }
        .launchIn(this)
}

suspend fun <T, R> Iterable<T>.parallelMap(transform: suspend CoroutineScope.(T) -> R) = coroutineScope {
    map { async { transform(it) } }.awaitAll()
}

internal suspend fun <T> Iterable<T>.parallelFilterNot(transform: suspend (T) -> Boolean) =
    channelFlow { parallelForEach { if (!transform(it)) send(it) } }.toList()

internal suspend fun <T, R> Iterable<T>.parallelMapNotNull(transform: suspend (T) -> R?) =
    channelFlow { parallelForEach { transform(it)?.let { send(it) } } }.toList()

internal suspend fun <T> Iterable<T>.parallelForEach(action: suspend CoroutineScope.(T) -> Unit) = coroutineScope {
    forEach { launch { action(it) } }
}

internal suspend fun <T, R, K> Map<T, R>.parallelMap(transform: suspend (Map.Entry<T, R>) -> K) = coroutineScope {
    map { async { transform(it) } }.awaitAll()
}

internal suspend fun <T, R> Iterable<T>.parallelFlatMap(transform: suspend (T) -> Iterable<R>) = coroutineScope {
    map { async { transform(it) } }.flatMap { it.await() }
}

internal suspend inline fun <K, V> Map<K, V>.parallelUpdatedKeys(keys: Iterable<K>, crossinline action: suspend (K) -> V): Map<K, V> {
    val map = toMutableMap()
    keys.parallelForEach { map[it] = action(it) }
    return map
}

internal fun timer(each: Duration, emitAtStartup: Boolean = true) = flow {
    if (emitAtStartup) emit(Unit)
    while (true) {
        delay(each)
        emit(Unit)
    }
}

internal fun <T> Flow<T>.throttle(time: Duration) =
    throttle(time.inWholeMilliseconds)

internal inline fun <reified T, reified R> Flow<T>.modifiedBy(
    modifierFlow: Flow<R>,
    noinline transform: suspend (T, R) -> T
): Flow<T> = flatMapLatest { modifierFlow.scan(it, transform) }

internal fun <T, R> Flow<T>.mapLatestTimedWithLoading(
    loggingContext: String,
    loadingFlow: MutableStateFlow<Boolean>? = null,
    transform: suspend CoroutineScope.(T) -> R
) =
    mapLatest {
        measureTimedValue {
            loadingFlow?.emit(true)
            val result = try {
                coroutineScope { transform(it) }
            } finally {
                loadingFlow?.emit(false)
            }
            result
        }
    }.map {
        logDebug(loggingContext) { "Took ${it.duration.absoluteValue} to process" }
        it.value
    }

internal fun <T> Flow<T>.catchAndLog(context: String, message: String? = null) =
    catch {
        if (message != null) {
            logDebug(context, it) { message }
        } else logDebug(context, it)
    }

internal suspend inline fun <R> MutableStateFlow<Boolean>.whileLoading(action: () -> R): TimedValue<R> {
    emit(true)
    val r = measureTimedValue { action() }
    emit(false)
    return r
}

internal inline fun <reified T> Flow<T>.batchAtIntervals(duration: Duration) = channelFlow {
    val mutex = Mutex()
    val buffer = mutableListOf<T>()
    var job: Job? = null
    collect {
        mutex.withLock { buffer.add(it) }
        if (job == null || job?.isCompleted == true) {
            job = launch {
                delay(duration)
                mutex.withLock {
                    send(buffer.toTypedArray())
                    buffer.clear()
                }
            }
        }
    }
}

internal suspend fun showBackgroundLoadingBar(
    project: Project,
    @Nls title: String,
    @Nls upperMessage: String,
    cancellable: Boolean = false,
    isSafe: Boolean = true
): BackgroundLoadingBarController {
    val syncSignal = Mutex(true)
    val externalScopeJob = coroutineContext.job
    val progressManager = ProgressManager.getInstance()
    val progressController = BackgroundLoadingBarController(syncSignal)
    progressManager.run(object : Task.Backgroundable(project, title, cancellable) {
        override fun run(indicator: ProgressIndicator) {
            if (isSafe && progressManager is ProgressManagerImpl && indicator is UserDataHolder) {
                progressManager.markProgressSafe(indicator)
            }
            indicator.text = upperMessage // ??? why does it work?
            runBlocking {
                indicator.text = upperMessage // ??? why does it work?
                val indicatorCancelledPollingJob = launch {
                    while (true) {
                        if (indicator.isCanceled) break
                        delay(50)
                    }
                }
                val internalJob = launch {
                    syncSignal.lock()
                }
                select {
                    internalJob.onJoin { }
                    externalScopeJob.onJoin {
                        internalJob.cancel()
                        indicatorCancelledPollingJob.cancel()
                    }
                    indicatorCancelledPollingJob.onJoin {
                        syncSignal.unlock()
                        progressController.triggerCallbacks()
                    }
                }
                indicatorCancelledPollingJob.cancel()
            }
        }
    })
    return progressController
}

internal class BackgroundLoadingBarController(private val syncMutex: Mutex) {

    private val callbacks = mutableSetOf<() -> Unit>()

    fun addOnComputationInterruptedCallback(callback: () -> Unit) {
        callbacks.add(callback)
    }

    internal fun triggerCallbacks() = callbacks.forEach { it.invoke() }

    fun clear() {
        runCatching { syncMutex.unlock() }
    }
}

suspend fun <R> writeAction(action: () -> R): R = withContext(Dispatchers.EDT) { action() }

internal fun ModuleTransformer.asCoroutine() = object : CoroutineModuleTransformer {
    override suspend fun transformModules(project: Project, nativeModules: List<Module>): List<ProjectModule> =
        this@asCoroutine.transformModules(project, nativeModules)
}

internal fun AsyncModuleTransformer.asCoroutine() = object : CoroutineModuleTransformer {
    override suspend fun transformModules(project: Project, nativeModules: List<Module>): List<ProjectModule> =
        this@asCoroutine.transformModules(project, nativeModules).await()
}

internal fun ModuleChangesSignalProvider.asCoroutine() = object : FlowModuleChangesSignalProvider {
    override fun registerModuleChangesListener(project: Project) = callbackFlow {
        val sub = registerModuleChangesListener(project) { trySend() }
        awaitClose { sub.unsubscribe() }
    }
}

internal fun ProjectModuleOperationProvider.asCoroutine() = object : CoroutineProjectModuleOperationProvider {

    override fun hasSupportFor(project: Project, psiFile: PsiFile?) = this@asCoroutine.hasSupportFor(project, psiFile)

    override fun hasSupportFor(projectModuleType: ProjectModuleType) = this@asCoroutine.hasSupportFor(projectModuleType)

    override suspend fun addDependencyToModule(operationMetadata: DependencyOperationMetadata, module: ProjectModule) =
        this@asCoroutine.addDependencyToModule(operationMetadata, module).toList()

    override suspend fun removeDependencyFromModule(operationMetadata: DependencyOperationMetadata, module: ProjectModule) =
        this@asCoroutine.removeDependencyFromModule(operationMetadata, module).toList()

    override suspend fun updateDependencyInModule(operationMetadata: DependencyOperationMetadata, module: ProjectModule) =
        this@asCoroutine.updateDependencyInModule(operationMetadata, module).toList()

    override suspend fun declaredDependenciesInModule(module: ProjectModule) =
        this@asCoroutine.declaredDependenciesInModule(module).toList()

    override suspend fun resolvedDependenciesInModule(module: ProjectModule, scopes: Set<String>) =
        this@asCoroutine.resolvedDependenciesInModule(module, scopes).toList()

    override suspend fun addRepositoryToModule(repository: UnifiedDependencyRepository, module: ProjectModule) =
        this@asCoroutine.addRepositoryToModule(repository, module).toList()

    override suspend fun removeRepositoryFromModule(repository: UnifiedDependencyRepository, module: ProjectModule) =
        this@asCoroutine.removeRepositoryFromModule(repository, module).toList()

    override suspend fun listRepositoriesInModule(module: ProjectModule) =
        this@asCoroutine.listRepositoriesInModule(module).toList()
}

internal fun AsyncProjectModuleOperationProvider.asCoroutine() = object : CoroutineProjectModuleOperationProvider {

    override fun hasSupportFor(project: Project, psiFile: PsiFile?) = this@asCoroutine.hasSupportFor(project, psiFile)

    override fun hasSupportFor(projectModuleType: ProjectModuleType) = this@asCoroutine.hasSupportFor(projectModuleType)

    override suspend fun addDependencyToModule(operationMetadata: DependencyOperationMetadata, module: ProjectModule) =
        this@asCoroutine.addDependencyToModule(operationMetadata, module).await().toList()

    override suspend fun removeDependencyFromModule(operationMetadata: DependencyOperationMetadata, module: ProjectModule) =
        this@asCoroutine.removeDependencyFromModule(operationMetadata, module).await().toList()

    override suspend fun updateDependencyInModule(operationMetadata: DependencyOperationMetadata, module: ProjectModule) =
        this@asCoroutine.updateDependencyInModule(operationMetadata, module).await().toList()

    override suspend fun declaredDependenciesInModule(module: ProjectModule) =
        this@asCoroutine.declaredDependenciesInModule(module).await().toList()

    override suspend fun resolvedDependenciesInModule(module: ProjectModule, scopes: Set<String>) =
        this@asCoroutine.resolvedDependenciesInModule(module, scopes).await().toList()

    override suspend fun addRepositoryToModule(repository: UnifiedDependencyRepository, module: ProjectModule) =
        this@asCoroutine.addRepositoryToModule(repository, module).await().toList()

    override suspend fun removeRepositoryFromModule(repository: UnifiedDependencyRepository, module: ProjectModule) =
        this@asCoroutine.removeRepositoryFromModule(repository, module).await().toList()

    override suspend fun listRepositoriesInModule(module: ProjectModule) =
        this@asCoroutine.listRepositoriesInModule(module).await().toList()
}

fun SendChannel<Unit>.trySend() = trySend(Unit)
fun ProducerScope<Unit>.trySend() = trySend(Unit)

suspend fun SendChannel<Unit>.send() = send(Unit)
suspend fun ProducerScope<Unit>.send() = send(Unit)

data class CombineLatest3<A, B, C>(val a: A, val b: B, val c: C)

fun <A, B, C, Z> combineLatest(
    flowA: Flow<A>,
    flowB: Flow<B>,
    flowC: Flow<C>,
    transform: suspend (CombineLatest3<A, B, C>) -> Z
) = combine(flowA, flowB, flowC) { a, b, c -> CombineLatest3(a, b, c) }
    .mapLatest(transform)

fun <T> Flow<T>.pauseOn(flow: Flow<Boolean>): Flow<T> = flow {
    coroutineScope {
        val buffer = produce { collect { send(it) } }
        flow.flatMapLatest { isOpen -> if (isOpen) buffer.receiveAsFlow() else EMPTY_UNCLOSED_FLOW }
            .collect { emit(it) }
    }
}

private val EMPTY_UNCLOSED_FLOW = flow<Nothing> {
    suspendCancellableCoroutine { }
}