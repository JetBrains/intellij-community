// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.toPromise
import java.util.function.Supplier

/**
 * Runs [block] in a project-scoped coroutine and returns a [Promise].
 *
 * The coroutine establishes a [kotlinx.coroutines.Job] in the context, which is required by
 * [com.intellij.openapi.progress.runBlockingCancellable] (used, e.g., in
 * [com.intellij.execution.wsl.target.WslTargetEnvironment.createProcess]).
 */
@ApiStatus.Internal
fun <T> asyncPromise(project: Project, block: suspend CoroutineScope.() -> T): Promise<T> {
  return project.service<PythonRunnerCoroutineScope>().cs.async(block = block).toPromise()
}

/**
 * Java-friendly variant of [asyncPromise]: runs [supplier] in a project-scoped coroutine.
 */
@ApiStatus.Internal
fun <T> runAsync(project: Project, supplier: Supplier<T>): Promise<T> {
  return asyncPromise(project) { supplier.get() }
}

@Service(Service.Level.PROJECT)
private class PythonRunnerCoroutineScope(val cs: CoroutineScope)
