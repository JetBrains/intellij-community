package circlet.runtime

import circlet.utils.application
import com.intellij.mock.MockApplication
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.util.Condition
import junit.framework.TestCase
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class TestApplicationDispatcher : TestCase() {
    private val dispatcher: ApplicationDispatcher

    init {
        ApplicationManager.setApplication(TestApplication()) {}
        dispatcher = ApplicationDispatcher(application)
    }

    fun testCoroutineDispatched() = runBlocking {
        val job = async(dispatcher.coroutineContext) {
            application.assertIsDispatchThread()
        }
        job.await()
    }

    fun testCoroutineDelay() = runBlocking {
        val job = async(dispatcher.coroutineContext) {
            delay(10)
            application.assertIsDispatchThread()
        }
        job.await()
    }

    fun testDispatch() {
        val runner = DispatchRunner(CountDownLatch(1))
        dispatcher.dispatch {
            runner.run {
                application.assertIsDispatchThread()
            }
        }
        runner.assertNoError()
    }

    fun testDispatchWithDelay() {
        val runner = DispatchRunner(CountDownLatch(1))
        dispatcher.dispatch(10) {
            runner.run {
                application.assertIsDispatchThread()
            }
        }
        runner.assertNoError()
    }

    fun testDispatchInterval() {
        val runner = DispatchRunner(CountDownLatch(2))
        val cancellable = dispatcher.dispatchInterval(10, 10) {
            runner.run {
                application.assertIsDispatchThread()
            }
        }
        runner.assertNoError()
        cancellable.cancel()
    }

    private class DispatchRunner(val latch: CountDownLatch) {
        var error: AssertionError? = null

        fun run(runnable: () -> Unit) {
            try {
                runnable()
            }
            catch (ae: AssertionError) {
                error = ae
                throw ae
            } finally {
                latch.countDown()
            }
        }

        fun assertNoError() {
            latch.await()
            if (error != null) {
                throw error!!
            }
        }
    }
}

private const val TEST_UI_DISPATCHER_NAME = "test ui dispatcher"

private class TestApplication : MockApplication({}) {
    val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, TEST_UI_DISPATCHER_NAME)
    }

    override fun assertIsDispatchThread() {
        assertTrue(Thread.currentThread().name.startsWith(TEST_UI_DISPATCHER_NAME))
    }

    override fun assertIsNonDispatchThread() {
        assertFalse(Thread.currentThread().name.startsWith(TEST_UI_DISPATCHER_NAME))
    }

    override fun invokeLater(runnable: Runnable) {
        executor.execute(runnable)
    }


    override fun invokeLater(runnable: java.lang.Runnable, expired: Condition<*>) {
        executor.execute(runnable)
    }

    override fun invokeLater(runnable: java.lang.Runnable, state: ModalityState, expired: Condition<*>) {
        executor.execute(runnable)
    }

    override fun invokeLater(runnable: java.lang.Runnable, state: ModalityState) {
        executor.execute(runnable)
    }
}
