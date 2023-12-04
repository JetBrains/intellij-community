// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.namedChildScope
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import java.util.function.Consumer

@Service(Service.Level.PROJECT)
class ParallelRunner(val project: Project, val cs: CoroutineScope) {


  suspend fun <T> runInParallel(collection: Collection<T>, method: suspend (T) -> Unit) {
    val runScope = cs.namedChildScope("ParallelRunner.runInParallel", Dispatchers.IO, true)
    collection.map {
      runScope.async {
        method(it)
      }
    }.awaitAll()
  }

  @RequiresBackgroundThread
  fun <T> runInParallelBlocking(collection: Collection<T>, method: Consumer<T>) = runBlockingMaybeCancellable {
    runInParallel(collection) {
      method.accept(it)
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.service<ParallelRunner>()
  }
}