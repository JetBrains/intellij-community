package circlet.runtime

import circlet.klogging.impl.*
import com.intellij.openapi.application.*
import kotlinx.coroutines.experimental.*
import runtime.*
import java.util.concurrent.*
import kotlin.coroutines.experimental.*

class ApplicationDispatcher(private val application: Application) : Dispatcher {
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        val thread = Thread(runnable, "Application Auxiliary Scheduler")

        thread.isDaemon = true
        thread
    }

    private val context = ApplicationCoroutineContext(application, executor)
    private val contextWithErrorToWarningLog = context + CoroutineExceptionLogger.create(ERROR_TO_WARNING_LOG)
    val contextWithExplicitLog: CoroutineContext = context + CoroutineExceptionLogger.create(EXPLICIT_LOG)

    override val coroutineContext: CoroutineContext
        get() = contextWithErrorToWarningLog

    override fun dispatch(runnable: () -> Unit) {
        application.invokeLater(runnable)
    }

    override fun dispatch(delay: Int, r: () -> Unit): Cancellable {
        val invoke = java.lang.Runnable {
            application.invokeLater(r)
        }
        val disposable = executor.schedule(invoke, delay.toLong(), TimeUnit.MILLISECONDS)

        return TaskCancellable(disposable)
    }

    override fun dispatchInterval(delay: Int, interval: Int, r: () -> Unit): Cancellable {
        val invoke = java.lang.Runnable {
            application.invokeLater(r)
        }
        val disposable = executor.scheduleAtFixedRate(invoke, delay.toLong(), interval.toLong(), TimeUnit.MILLISECONDS)

        return TaskCancellable(disposable)
    }
}

private class ApplicationCoroutineContext(
    private val application: Application,
    private val executor: ScheduledExecutorService
) : CoroutineDispatcher(), Delay {

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        application.invokeLater(block)
    }

    override fun scheduleResumeAfterDelay(time: Long, unit: TimeUnit, continuation: CancellableContinuation<Unit>) {
        val toResume = java.lang.Runnable {
            application.invokeLater {
                with(continuation) { this@ApplicationCoroutineContext.resumeUndispatched(Unit) }
            }
        }

        executor.schedule(toResume, time, unit)
    }

    override fun invokeOnTimeout(time: Long, unit: TimeUnit, block: Runnable): DisposableHandle {
        val toResume = java.lang.Runnable {
            application.invokeLater {
                block.run()
            }
        }

        return DisposableFutureHandle(executor.schedule(toResume, time, unit))
    }
}

private const val LOG_NAME = "circlet.runtime.ApplicationDispatcherKt"
private val ERROR_TO_WARNING_LOG = ErrorToWarningKLoggers.logger(LOG_NAME)
private val EXPLICIT_LOG = ExplicitKLoggers.logger(LOG_NAME)
