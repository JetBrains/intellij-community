// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.util.coroutines.namedChildScope
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore

@Service(Service.Level.PROJECT)
class ParallelRunner(val project: Project, val cs: CoroutineScope) {

  suspend fun <T> runInParallel(collection: Collection<T>, method: suspend (T) -> Unit) {
    if (collection.isEmpty()) return;
    if (collection.size == 1) {
      method.invoke(collection.first())
    }
    else {
      val maxParallel = getMaxParallel()
      if (maxParallel == 1) {
        collection.forEach {
          method(it)
        }
      }
      else {
        val runScope = cs.namedChildScope("ParallelRunner.runInParallel", Dispatchers.IO, true)
        val semaphore = Semaphore(maxParallel)
        collection.map {
          semaphore.acquire()
          runScope.async {
            method(it)
            semaphore.release()
          }
        }.awaitAll()
        runScope.cancel()
      }

    }

  }

  private fun getMaxParallel(): Int {
    val registryValue = Registry.intValue("maven.max.parallel.tasks")
    if (registryValue <= 0) {
      return Math.max(Runtime.getRuntime().availableProcessors() - 1, 1)
    }
    return registryValue
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.service<ParallelRunner>()
  }
}