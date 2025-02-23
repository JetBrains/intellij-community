@file:JvmName("SharedFlowSimple")
package flows.sharedFlow.simple

// ATTACH_LIBRARY: maven(com.intellij.platform:kotlinx-coroutines-core-jvm:1.8.0-intellij-11)

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

val flow = MutableSharedFlow<Int>()

fun main(): Unit = runBlocking {
    val job = launch {
        flow.collect {
            //Breakpoint!
            println(it)
        }
    }

    launch {
        `~~~!!! recognizableFrameWithEmitInAsyncStackTrace 1 !!!~~~`(flow, 0)
        delay(100)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTrace 2 !!!~~~`(flow, 1)
        delay(100)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTrace 3 !!!~~~`(flow, 2)
        delay(100)
        job.cancel()
    }
}

suspend fun <T> `~~~!!! recognizableFrameWithEmitInAsyncStackTrace 1 !!!~~~`(flow: MutableSharedFlow<T>, value: T) {
    flow.emit(value)
}

suspend fun <T> `~~~!!! recognizableFrameWithEmitInAsyncStackTrace 2 !!!~~~`(flow: MutableSharedFlow<T>, value: T) {
    flow.emit(value)
}

suspend fun <T> `~~~!!! recognizableFrameWithEmitInAsyncStackTrace 3 !!!~~~`(flow: MutableSharedFlow<T>, value: T) {
    flow.emit(value)
}
