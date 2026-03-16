// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.test.env.core

import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

/**
 * Wrapper around [PyEnvironment] that prevents closing the wrapped environment.
 * Used to return cached environments that should remain alive for reuse.
 */
@ApiStatus.Internal
private class NonClosingPyEnvironmentWrapper(private val delegate: PyEnvironment) : PyEnvironment by delegate {
  override fun close() {
    // Do nothing - the cached environment must stay alive
  }
  
  override fun <T : PyEnvironment> unwrap(): T? {
    @Suppress("UNCHECKED_CAST")
    return delegate as? T
  }
}

/**
 * Global cache for Python test environments.
 * 
 * Environments are cached based on their specification and reused across multiple tests.
 * Environments remain alive until explicitly released or JVM shutdown.
 * 
 * JUnit integration should:
 * - Call [releaseEnvironment] after each test if immediate cleanup is desired
 * - Call [releaseAll] at the end of test suite for full cleanup
 * - JVM shutdown hook provides last-resort cleanup
 */
@ApiStatus.Internal
internal class PyEnvironmentCache: AutoCloseable {
  private val LOG = Logger.getInstance(PyEnvironmentCache::class.java)

  private val cache = ConcurrentHashMap<CacheKey, PyEnvironment>()

  init {
    // Register shutdown hook as last resort cleanup
    Runtime.getRuntime().addShutdownHook(Thread {
      releaseAll()
    })
  }

  /**
   * Get or create an environment for the given specification.
   * Once created, the environment is kept alive until explicitly released.
   * 
   * @param cacheKey Unique key identifying this environment configuration
   * @param creator Suspend function to create the environment if not cached
   * @return Result containing the cached or newly created environment wrapped in a no-op close wrapper
   */
  suspend fun getOrCreate(
    cacheKey: CacheKey,
    creator: suspend () -> PyEnvironment,
  ): PyEnvironment {
    // Fast path: check if already cached
    cache[cacheKey]?.let { environment ->
      LOG.info("Environment cache HIT for key: $cacheKey, path: ${environment.pythonPath}")
      return NonClosingPyEnvironmentWrapper(environment)
    }

    // Slow path: create new environment
    LOG.info("Environment cache MISS for key: $cacheKey, creating new environment...")
    val startTime = System.currentTimeMillis()

    val environment = creator()
    val creationTime = (System.currentTimeMillis() - startTime) / 1000.0

    // Store in cache, or return existing if another thread created it first
    val existingOrNew = cache.putIfAbsent(cacheKey, environment)
    return if (existingOrNew == null) {
      LOG.info("Environment CREATED in %.2f seconds for key: %s, path: %s".format(creationTime, cacheKey, environment.pythonPath))
      NonClosingPyEnvironmentWrapper(environment)
    }
    else {
      LOG.info("Environment already created by another thread for key: $cacheKey")
      NonClosingPyEnvironmentWrapper(existingOrNew)
    }
  }

  /**
   * Release a specific environment from cache and close it.
   * 
   * @param cacheKey Unique key identifying the environment to release
   */
  fun releaseEnvironment(cacheKey: CacheKey) {
    cache.remove(cacheKey)?.let { environment ->
      LOG.info("Releasing environment for key: $cacheKey, path: ${environment.pythonPath}")
      try {
        environment.close()
        LOG.info("Environment disposed successfully for key: $cacheKey")
      }
      catch (e: Exception) {
        LOG.warn("Failed to dispose environment for key: $cacheKey", e)
      }
    }
  }

  /**
   * Release all cached environments and close them.
   * Should be called by JUnit integration at end of test suite.
   */
  fun releaseAll() {
    val count = cache.size
    if (count == 0) {
      LOG.info("No cached environments to release")
      return
    }

    LOG.info("Releasing all $count cached environments...")
    var successCount = 0
    var failCount = 0

    cache.values.forEach { environment ->
      try {
        environment.close()
        successCount++
      }
      catch (e: Exception) {
        LOG.warn("Failed to dispose environment at ${environment.pythonPath}", e)
        failCount++
      }
    }
    cache.clear()

    LOG.info("Environment cache cleanup complete: $successCount disposed successfully, $failCount failed")
  }

  override fun close() {
    releaseAll()
  }
}
