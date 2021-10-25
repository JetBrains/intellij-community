package com.jetbrains.packagesearch.intellij.plugin.util

import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.impl.coroutineDispatchingContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.codehaus.groovy.runtime.memoize.LRUCache
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration

internal fun <T> Flow<T>.onEach(context: CoroutineContext, action: suspend (T) -> Unit) =
    onEach { withContext(context) { action(it) } }

internal fun <T, R> Flow<T>.map(context: CoroutineContext, action: suspend (T) -> R) =
    map { withContext(context) { action(it) } }

@Suppress("unused") // The receiver is technically unused
internal val Dispatchers.AppUI
    get() = AppUIExecutor.onUiThread().coroutineDispatchingContext()

internal fun <T> Flow<T>.replayOnSignals(vararg signals: Flow<Any>) = channelFlow {
    var lastValue: T? = null
    onEach { send(it) }
        .onEach { lastValue = it }
        .launchIn(this)

    merge(*signals).mapNotNull { lastValue }
        .onEach { send(it) }
        .launchIn(this)
}

@Suppress("UNCHECKED_CAST")
fun <T1, T2, T3, T4, T5, T6, T7, R> combineTyped(
    flow: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    flow6: Flow<T6>,
    flow7: Flow<T7>,
    transform: suspend (T1, T2, T3, T4, T5, T6, T7) -> R
): Flow<R> = combine(flow, flow2, flow3, flow4, flow5, flow6, flow7) { args: Array<Any?> ->
    transform(
        args[0] as T1,
        args[1] as T2,
        args[2] as T3,
        args[3] as T4,
        args[4] as T5,
        args[5] as T6,
        args[6] as T7
    )
}

@Suppress("UNCHECKED_CAST")
fun <T1, T2, T3, T4, T5, T6, T7, T8, R> combineTyped(
    flow: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    flow6: Flow<T6>,
    flow7: Flow<T7>,
    flow8: Flow<T8>,
    transform: suspend (T1, T2, T3, T4, T5, T6, T7, T8) -> R
): Flow<R> = combine(flow, flow2, flow3, flow4, flow5, flow6, flow7, flow8) { args: Array<Any?> ->
    transform(
        args[0] as T1,
        args[1] as T2,
        args[2] as T3,
        args[3] as T4,
        args[4] as T5,
        args[5] as T6,
        args[6] as T7,
        args[7] as T8
    )
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

@Suppress("UNCHECKED_CAST")
class CoroutineLRUCache<K : Any, V>(maxSize: Int) {

    private val cache = LRUCache(maxSize)
    private val syncMutex = Mutex()
    private val mutexMap = mutableMapOf<K, Mutex>().withDefault { Mutex() }

    private suspend inline fun <R> withLock(key: K, action: () -> R) =
        syncMutex.withLock { mutexMap.getValue(key) }.withLock { action() }

    suspend fun getOrNull(key: K): V? = withLock(key) { cache.get(key) as? V }

    suspend fun get(key: K): V = checkNotNull(getOrNull(key)) { "Key $key not available" }

    suspend fun put(key: K, value: V) {
        withLock(key) { cache.put(key, value) }
    }

    suspend fun getOrElse(key: K, default: suspend () -> V) = withLock(key) {
        val value = cache.get(key) as? V
        value ?: default()
    }

    suspend fun getOrPut(key: K, default: suspend () -> V) = withLock(key) {
        val value = cache.get(key) as? V
        value ?: default().also { cache.put(key, it) }
    }

    suspend fun getOrTryPutDefault(key: K, default: suspend () -> V) = withLock(key) {
        val value = cache.get(key) as? V

        value ?: runCatching { default() }.getOrNull()?.also { cache.put(key, it) }
    }

    suspend fun clear() = syncMutex.withLock {
        for (key in mutexMap.keys) {
            cache.put(key, null)
        }
        mutexMap.clear()
        cache.cleanUpNullReferences()
    }
}

internal fun timer(each: Duration, emitAtStartup: Boolean = true) = flow {
    if (emitAtStartup) emit(Unit)
    while (true) {
        delay(each)
        emit(Unit)
    }
}
