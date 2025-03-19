@file:JvmName("SharedFlowComples")
package flows.sharedFlow.comples

// ATTACH_LIBRARY: maven(com.intellij.platform:kotlinx-coroutines-core-jvm:1.8.0-intellij-11)

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

val flow = MutableSharedFlow<Int>(replay = 2)

fun main(): Unit = runBlocking {
    val job = launch {
        flow
            .map { it * 2 }
            .filter { it % 2 == 0 }
            .scan(0) { accumulator, value -> accumulator + value }
            .buffer(2)
            .collect {
                //Breakpoint!
                println(it)
            }
    }

    launch {
        delay(100)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTrace 1 !!!~~~`(flow, 1)
        delay(100)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTrace 2 !!!~~~`(flow, 2)
        delay(100)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTrace 3 !!!~~~`(flow, 3)
        delay(100)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTrace 4 !!!~~~`(flow, 4)
        delay(100)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTrace 5 !!!~~~`(flow, 5)
        delay(100)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTrace 6 !!!~~~`(flow, 6)
        delay(100)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTrace 7 !!!~~~`(flow, 7)
        delay(100)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTrace 8 !!!~~~`(flow, 8)
        delay(100)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTrace 9 !!!~~~`(flow, 9)
        delay(100)
        `~~~!!! recognizableFrameWithEmitInAsyncStackTrace 10 !!!~~~`(flow, 10)
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

suspend fun <T> `~~~!!! recognizableFrameWithEmitInAsyncStackTrace 4 !!!~~~`(flow: MutableSharedFlow<T>, value: T) {
    flow.emit(value)
}

suspend fun <T> `~~~!!! recognizableFrameWithEmitInAsyncStackTrace 5 !!!~~~`(flow: MutableSharedFlow<T>, value: T) {
    flow.emit(value)
}

suspend fun <T> `~~~!!! recognizableFrameWithEmitInAsyncStackTrace 6 !!!~~~`(flow: MutableSharedFlow<T>, value: T) {
    flow.emit(value)
}

suspend fun <T> `~~~!!! recognizableFrameWithEmitInAsyncStackTrace 7 !!!~~~`(flow: MutableSharedFlow<T>, value: T) {
    flow.emit(value)
}

suspend fun <T> `~~~!!! recognizableFrameWithEmitInAsyncStackTrace 8 !!!~~~`(flow: MutableSharedFlow<T>, value: T) {
    flow.emit(value)
}

suspend fun <T> `~~~!!! recognizableFrameWithEmitInAsyncStackTrace 9 !!!~~~`(flow: MutableSharedFlow<T>, value: T) {
    flow.emit(value)
}

suspend fun <T> `~~~!!! recognizableFrameWithEmitInAsyncStackTrace 10 !!!~~~`(flow: MutableSharedFlow<T>, value: T) {
    flow.emit(value)
}
