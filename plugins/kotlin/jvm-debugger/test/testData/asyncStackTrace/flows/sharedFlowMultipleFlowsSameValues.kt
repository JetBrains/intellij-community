@file:JvmName("SharedFlowMultipleFlowsSameValues")
package flows.sharedFlow.sharedFlowMultipleFlowsSameValues

// ATTACH_LIBRARY: maven(com.intellij.platform:kotlinx-coroutines-core-jvm:1.8.0-intellij-11)

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

val flow1 = MutableSharedFlow<Int>()
val flow2 = MutableSharedFlow<Int>()

fun main(): Unit = runBlocking {
    val job1 = launch {
        flow1.collect {
            //Breakpoint!
            println(it)
        }
    }
    val job2 = launch {
        flow2.collect {
            //Breakpoint!
            println(it)
        }
    }

    launch {
        `~~~!!! recognizableFrameWithEmitInAsyncStackTrace 1 !!!~~~`(flow1, 42)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTrace 2 !!!~~~`(flow2, 42)
        delay(10)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTrace 3 !!!~~~`(flow1, 42)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTrace 4 !!!~~~`(flow2, 42)
        delay(10)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTrace 5 !!!~~~`(flow1, 42)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTrace 6 !!!~~~`(flow2, 42)
        delay(10)
        job1.cancel()
        job2.cancel()
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

suspend fun <T> `~~~!!! recognizableFrameWithEmitInAsyncStackTrace 4 !!!~~~`(flow: MutableSharedFlow<T>, value: T) {
    flow.emit(value)
}

suspend fun <T> `~~~!!! recognizableFrameWithEmitInAsyncStackTrace 5 !!!~~~`(flow: MutableSharedFlow<T>, value: T) {
    flow.emit(value)
}

suspend fun <T> `~~~!!! recognizableFrameWithEmitInAsyncStackTrace 6 !!!~~~`(flow: MutableSharedFlow<T>, value: T) {
    flow.emit(value)
}
