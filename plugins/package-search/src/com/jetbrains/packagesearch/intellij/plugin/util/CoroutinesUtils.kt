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

import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.CoroutineSuspender
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.impl.ProgressManagerImpl
import com.intellij.openapi.progress.impl.ProgressSuspender
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolder
import com.intellij.util.flow.throttle
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.data.LoadingContainer
import com.jetbrains.packagesearch.intellij.plugin.extensibility.AsyncModuleTransformer
import com.jetbrains.packagesearch.intellij.plugin.extensibility.FlowModuleChangesSignalProvider
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ModuleChangesSignalProvider
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ModuleTransformer
import com.jetbrains.packagesearch.intellij.plugin.extensibility.PackageSearchModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.future.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.properties.ReadOnlyProperty
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private val ProgressSuspender.isSuspendedFLow: Flow<Boolean>
    get() = flow {
        emit(isSuspended)
        while (currentCoroutineContext().isActive) {
            delay(50.milliseconds)
            emit(isSuspended)
        }
    }

private suspend fun ProgressIndicator.awaitCancellation() {
    while (currentCoroutineContext().isActive) {
        if (isCanceled) return
        delay(50.milliseconds)
    }
}

internal fun <T> Flow<T>.onEach(context: CoroutineContext, action: suspend (T) -> Unit) =
    onEach { withContext(context) { action(it) } }

internal fun <T, R> Flow<T>.map(context: CoroutineContext, action: suspend (T) -> R) =
    map { withContext(context) { action(it) } }

suspend fun <T, R> Iterable<T>.parallelMap(transform: suspend CoroutineScope.(T) -> R) = coroutineScope {
    map { async { transform(it) } }.awaitAll()
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

internal fun <T, R> Flow<T>.modifiedBy(
    modifierFlow: Flow<R>,
    loadingContainer: LoadingContainer? = null,
    transform: suspend (T, R) -> T
): Flow<T> {
    val loadingFlow = loadingContainer?.addLoadingState()
    return flatMapLatest { modifierFlow.scan(it) { a, b -> loadingFlow?.whileLoading { transform(a, b) } ?: transform(a, b) } }
}

internal inline fun <T, R> Flow<T>.map(loadingContainer: LoadingContainer, crossinline transform: suspend (value: T) -> R): Flow<R> {
    val loadingFlow = loadingContainer.addLoadingState()
    return map { loadingFlow.whileLoading { transform(it) } }
}

internal fun <T> Flow<T>.catchAndLog(message: String? = null): ReadOnlyProperty<Any, Flow<T>> = ReadOnlyProperty { thisRef, property ->
    catch {
        logDebug("${thisRef::class.simpleName}#${property.name}", it, message)
    }
}

internal fun <T> Flow<T>.shareInAndCatchAndLog(
    scope: CoroutineScope,
    started: SharingStarted,
    reply: Int = 0,
    message: String? = null
): ReadOnlyProperty<Any, SharedFlow<T>> = ReadOnlyProperty { thisRef, property ->
    catch { logDebug("${thisRef::class.simpleName}#${property.name}", it, message)  }
        .shareIn(scope, started, reply)
}

internal fun <T> Flow<T>.stateInAndCatchAndLog(
    scope: CoroutineScope,
    started: SharingStarted,
    initialValue: T,
    message: String? = null
): ReadOnlyProperty<Any, StateFlow<T>> = ReadOnlyProperty { thisRef, property ->
    catch { logDebug("${thisRef::class.simpleName}#${property.name}", it, message) }
        .stateIn(scope, started, initialValue)
}

internal suspend inline fun <R> MutableStateFlow<LoadingContainer.LoadingState>.whileLoading(action: () -> R): R {
    emit(LoadingContainer.LoadingState.LOADING)
    return try {
        action()
    } finally {
        emit(LoadingContainer.LoadingState.IDLE)
    }
}

internal inline fun <reified T> Flow<T>.debounceBatch(duration: Duration) = channelFlow {
    val mutex = Mutex()
    val buffer = mutableListOf<T>()
    var job: Job? = null
    collect {
        mutex.withLock {
            buffer.add(it)
            job?.cancel()
            job = launch {
                delay(duration)
                mutex.withLock {
                    send(buffer.toList())
                    buffer.clear()
                }
            }
        }
    }
}

internal suspend inline fun <T> withBackgroundLoadingBar(
    project: Project,
    @Nls title: String,
    @Nls upperMessage: String? = null,
    cancellable: Boolean = false,
    isSafe: Boolean = true,
    isIndeterminate: Boolean = true,
    isPausable: Boolean = false,
    action: BackgroundLoadingBarController.() -> T
): T {
    val controller = showBackgroundLoadingBar(
        project = project,
        title = title,
        upperMessage = upperMessage,
        cancellable = cancellable,
        isSafe = isSafe,
        isIndeterminate = isIndeterminate,
        isPausable = isPausable
    )
    return try {
        controller.action()
    } finally {
        controller.clear()
    }
}

internal suspend fun showBackgroundLoadingBar(
    project: Project,
    @Nls title: String,
    @Nls upperMessage: String? = null,
    cancellable: Boolean = false,
    isSafe: Boolean = true,
    isIndeterminate: Boolean = true,
    isPausable: Boolean = false
): BackgroundLoadingBarController {
    val syncSignal = Mutex(true)
    val externalScopeJob = coroutineContext.job
    val progressManager = ProgressManager.getInstance()
    val progressChannel = Channel<Double>()
    val messageChannel = Channel<String>()
    val isSuspendedChannel = Channel<Boolean>()
    val channels = listOf(progressChannel, messageChannel, isSuspendedChannel)
    val progressController = BackgroundLoadingBarController(syncSignal, progressChannel, messageChannel, isSuspendedChannel.consumeAsFlow())
    progressManager.run(object : Task.Backgroundable(project, title, cancellable) {
        override fun run(indicator: ProgressIndicator) {
            if (isSafe && progressManager is ProgressManagerImpl && indicator is UserDataHolder) {
                progressManager.markProgressSafe(indicator)
            }
            indicator.isIndeterminate = isIndeterminate
            if (upperMessage != null) indicator.text = upperMessage // ??? why does it work?
            runBlocking {
                @Suppress("HardCodedStringLiteral")
                val channelsJob = launch {
                    if (isPausable) {
                        ProgressSuspender.markSuspendable(indicator, PackageSearchBundle.message("packagesearch.ui.resume"))
                            .isSuspendedFLow
                            .collectIn(this, isSuspendedChannel)
                    }
                    progressChannel.consumeAsFlow()
                        .onEach { indicator.fraction = it }
                        .launchIn(this)
                    messageChannel.consumeAsFlow()
                        .onEach { indicator.text = it }
                        .launchIn(this)
                }
                val userCancelledJob = launch { indicator.awaitCancellation() }
                val proceduralCancellationJob = launch { syncSignal.lock() }
                select {
                    proceduralCancellationJob.onJoin {
                        userCancelledJob.cancel()
                    }
                    externalScopeJob.onJoin {
                        proceduralCancellationJob.cancel()
                        userCancelledJob.cancel()
                    }
                    userCancelledJob.onJoin {
                        proceduralCancellationJob.cancel()
                        syncSignal.unlock()
                        progressController.triggerCallbacks()
                    }
                }
                channelsJob.cancel()
                channels.closeAll()
            }
        }
    })
    return progressController
}

private fun Iterable<Channel<*>>.closeAll() = forEach { it.close() }

internal class BackgroundLoadingBarController(
    private val syncMutex: Mutex,
    val progressChannel: SendChannel<Double>,
    val messageChannel: SendChannel<String>,
    val isSuspendedFLow: Flow<Boolean>
) {

    private val callbacks = mutableSetOf<() -> Unit>()

    fun addOnComputationInterruptedCallback(callback: () -> Unit) {
        callbacks.add(callback)
    }

    fun attachSuspender(coroutineScope: CoroutineScope, coroutineSuspender: CoroutineSuspender) =
        isSuspendedFLow.onEach { if (it) coroutineSuspender.pause() else coroutineSuspender.resume() }
            .launchIn(coroutineScope)

    internal fun triggerCallbacks() = callbacks.forEach { it.invoke() }

    fun clear() {
        runCatching { syncMutex.unlock() }
    }
}

@Deprecated("", ReplaceWith("com.intellij.openapi.application.writeAction(action)"))
suspend fun <R> writeAction(action: () -> R): R = com.intellij.openapi.application.writeAction { action() }

internal fun AsyncModuleTransformer.asCoroutine() = object : ModuleTransformer {
    override suspend fun transformModules(project: Project, nativeModules: List<Module>): List<PackageSearchModule> =
        this@asCoroutine.transformModules(project, nativeModules).await()
}

internal fun ModuleChangesSignalProvider.asCoroutine() = object : FlowModuleChangesSignalProvider {
    override fun registerModuleChangesListener(project: Project) = callbackFlow {
        val sub = registerModuleChangesListener(project) { trySend() }
        awaitClose { sub.unsubscribe() }
    }
}

fun ProducerScope<Unit>.trySend() = trySend(Unit)
fun SendChannel<Unit>.trySend() = trySend(Unit)

suspend fun SendChannel<Unit>.send() = send(Unit)
suspend fun ProducerScope<Unit>.send() = send(Unit)

internal fun <T> Flow<T>.collectIn(coroutineScope: CoroutineScope, sendChannel: SendChannel<T>) =
    onEach { sendChannel.send(it) }.launchIn(coroutineScope)

internal fun <T> Flow<T>.replayOn(replayFlow: Flow<*>) = channelFlow {
    val mutex = Mutex()
    var last: T? = null
    onEach { mutex.withLock { last = it } }.collectIn(this, this)
    replayFlow.collect { mutex.withLock { last?.let { send(it) } } }
}