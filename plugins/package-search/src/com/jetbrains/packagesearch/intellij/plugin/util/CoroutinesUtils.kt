@file:Suppress("FunctionName")

package com.jetbrains.packagesearch.intellij.plugin.util

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.impl.ProgressManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
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
import kotlin.math.max
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

    merge(*signals).mapNotNull { lastValue }
        .onEach { send(it) }
        .launchIn(this)
}

internal suspend fun <T, R> Iterable<T>.parallelMap(transform: suspend (T) -> R) = coroutineScope {
    map { async { transform(it) } }.awaitAll()
}

internal suspend fun <T> Iterable<T>.parallelFilterNot(transform: suspend (T) -> Boolean) =
    channelFlow { parallelForEach { if (!transform(it)) send(it) } }.toList()

internal suspend fun <T, R> Iterable<T>.parallelMapNotNull(transform: suspend (T) -> R?) =
    channelFlow { parallelForEach { transform(it)?.let { send(it) } } }.toList()

internal suspend fun <T> Iterable<T>.parallelForEach(action: suspend (T) -> Unit) = coroutineScope {
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

internal fun <T> Flow<T>.throttle(time: Duration, debounce: Boolean = true) =
    throttle(time.inWholeMilliseconds, debounce)

internal fun <T> Flow<T>.throttle(timeMillis: Int, debounce: Boolean = true) =
    throttle(timeMillis.toLong(), debounce)

internal fun <T> Flow<T>.throttle(timeMillis: Long, debounce: Boolean = true) = channelFlow {
    var last = System.currentTimeMillis() - timeMillis * 2
    var refireJob: Job? = null
    collect {
        val elapsedTime = System.currentTimeMillis() - last
        refireJob?.cancel()
        when {
            elapsedTime > timeMillis -> {
                send(it)
                last = System.currentTimeMillis()
            }
            debounce -> refireJob = launch {
                delay(max(timeMillis - elapsedTime, 0))
                send(it)
            }
        }
    }
}

internal inline fun <reified T, reified R> Flow<T>.modifiedBy(
    modifierFlow: Flow<R>,
    crossinline transform: suspend (T, R) -> T
): Flow<T> = flow {
    coroutineScope {
        val queue = Channel<Any?>(capacity = 1)

        val mutex = Mutex(locked = true)
        this@modifiedBy.onEach {
            queue.send(it)
            if (mutex.isLocked) mutex.unlock()
        }.launchIn(this)
        mutex.lock()
        modifierFlow.onEach { queue.send(it) }.launchIn(this)

        var currentState: T = queue.receive() as T
        emit(currentState)

        for (e in queue) {
            when (e) {
                is T -> currentState = e
                is R -> currentState = transform(currentState, e)
                else -> continue
            }
            emit(currentState)
        }
    }
}

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
        logTrace(loggingContext) { "Took ${it.duration.absoluteValue} to elaborate" }
        it.value
    }

internal fun <T> Flow<T>.catchAndLog(context: String, message: String, fallbackValue: T, retryChannel: SendChannel<Unit>? = null) =
    catch {
        logWarn(context, it) { message }
        retryChannel?.send(Unit)
        emit(fallbackValue)
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
    val upperMessageChannel = Channel<String>()
    val lowerMessageChannel = Channel<String>()
    val cancellationRequested = Channel<Unit>()
    val externalScopeJob = coroutineContext.job
    val progressManager = ProgressManager.getInstance()

    progressManager.run(object : Task.Backgroundable(project, title, cancellable) {
        override fun run(indicator: ProgressIndicator) {
            if (isSafe && progressManager is ProgressManagerImpl && indicator is UserDataHolder) {
                progressManager.markProgressSafe(indicator)
            }
            indicator.text = upperMessage // ??? why does it work?
            runBlocking {
                upperMessageChannel.consumeAsFlow().onEach { indicator.text = it }.launchIn(this)
                lowerMessageChannel.consumeAsFlow().onEach { indicator.text2 = it }.launchIn(this)
                indicator.text = upperMessage // ??? why does it work?
                val indicatorCancelledPollingJob = launch {
                    while (true) {
                        if (indicator.isCanceled) {
                            cancellationRequested.send(Unit)
                            break
                        }
                        delay(50)
                    }
                }
                val internalJob = launch {
                    syncSignal.lock()
                }
                select<Unit> {
                    internalJob.onJoin { }
                    externalScopeJob.onJoin { internalJob.cancel() }
                }
                indicatorCancelledPollingJob.cancel()
                upperMessageChannel.close()
                lowerMessageChannel.close()
            }
        }
    })
    return BackgroundLoadingBarController(
        syncSignal,
        upperMessageChannel,
        lowerMessageChannel,
        cancellationRequested.consumeAsFlow()
    )
}

internal class BackgroundLoadingBarController(
    private val syncMutex: Mutex,
    val upperMessageChannel: SendChannel<String>,
    val lowerMessageChannel: SendChannel<String>,
    val cancellationFlow: Flow<Unit>
) {

    fun clear() = runCatching { syncMutex.unlock() }.getOrElse { }
}
