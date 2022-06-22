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

    private inline val threadCount
        get() = max(1, 2 * Runtime.getRuntime().availableProcessors() / 3)

    private val dispatcher =
        AppExecutorUtil.createBoundedApplicationPoolExecutor(
            /* name = */ this::class.simpleName!!,
            /* maxThreads = */ threadCount
        ).asCoroutineDispatcher()

    private val supervisor = SupervisorJob()

    override val coroutineContext = supervisor + CoroutineName(this::class.qualifiedName!!) + dispatcher

    override fun dispose() {
        supervisor.invokeOnCompletion { dispatcher.close() }
        supervisor.cancel("Disposing ${this::class.simpleName}")
    }
}
