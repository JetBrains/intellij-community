@file:Suppress("EXPERIMENTAL_IS_NOT_ENABLED")

package com.jetbrains.packagesearch.intellij.plugin.lifecycle

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlin.math.max

@Service(Service.Level.PROJECT)
internal class PackageSearchLifecycleScope : CoroutineScope, Disposable {

    private inline val pkgsThreadCount
        get() = max(1, Runtime.getRuntime().availableProcessors() / 4)

    private val installedDependenciesExecutor =
        AppExecutorUtil.createBoundedApplicationPoolExecutor(
            /* name = */ this::class.simpleName!!,
            /* maxThreads = */ pkgsThreadCount
        ).asCoroutineDispatcher()

    private val supervisor = SupervisorJob()

    override val coroutineContext = supervisor + CoroutineName(this::class.qualifiedName!!) + installedDependenciesExecutor

    override fun dispose() {
        supervisor.invokeOnCompletion { installedDependenciesExecutor.close() }
        supervisor.cancel("Disposing ${this::class.simpleName}")
    }
}
