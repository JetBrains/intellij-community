package com.jetbrains.packagesearch.intellij.plugin.util

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.commons.collections.map.LRUMap

@Suppress("UNCHECKED_CAST")
class CoroutineLRUCache<K : Any, V>(maxSize: Int) {

    private val cache = LRUMap(maxSize)
    private val syncMutex = Mutex()
    private val mutexMap = mutableMapOf<K, Mutex>().withDefault { Mutex() }

    private suspend inline fun <R> withLock(key: K, action: () -> R): R {
        val mutex = syncMutex.withLock { mutexMap.getValue(key).apply { lock() } }
        val result = action()
        mutex.unlock()
        return result
    }

    suspend fun getOrNull(key: K): V? = withLock(key) { cache[key] as? V }

    suspend fun get(key: K): V = checkNotNull(getOrNull(key)) { "Key $key not available" }

    suspend fun put(key: K, value: V) {
        withLock(key) { cache.put(key, value) }
    }

    suspend fun getOrElse(key: K, default: suspend () -> V) = withLock(key) {
        val value = cache[key] as? V
        value ?: default()
    }

    suspend fun getOrPut(key: K, default: suspend () -> V) = withLock(key) {
        val value = cache[key] as? V
        value ?: default().also { cache[key] = it }
    }

    suspend fun getOrTryPutDefault(key: K, default: suspend () -> V) = withLock(key) {
        val value = cache[key] as? V

        value ?: runCatching { default() }.getOrNull()?.also { cache[key] = it }
    }

    suspend fun clear() {
        syncMutex.withLock {
            coroutineScope {
                launch { mutexMap.clear() }
                launch { cache.clear() }
            }
        }
    }
}
